package com.clearchit.service;

import com.clearchit.exception.BadRequestException;
import com.clearchit.exception.ChitGroupNotFoundException;
import com.clearchit.model.ChitGroup;
import com.clearchit.model.ChitStatus;
import com.clearchit.model.CycleStatus;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.ChitGroupRepository;
import com.clearchit.repository.MonthlyCycleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonthlyCycleService {

    private final MonthlyCycleRepository monthlyCycleRepository;
    private final ChitGroupRepository chitGroupRepository;
    private final ChitGroupMemberRepository chitGroupMemberRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final MonthlyPaymentService monthlyPaymentService;

    public MonthlyCycleService(
            MonthlyCycleRepository monthlyCycleRepository,
            ChitGroupRepository chitGroupRepository,
            ChitGroupMemberRepository chitGroupMemberRepository,
            AuthorizationService authorizationService,
            AuditService auditService,
            MonthlyPaymentService monthlyPaymentService) {

        this.monthlyCycleRepository = monthlyCycleRepository;
        this.chitGroupRepository = chitGroupRepository;
        this.chitGroupMemberRepository = chitGroupMemberRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.monthlyPaymentService = monthlyPaymentService;
    }

    @Transactional
    public MonthlyCycle createCycle(
            Long groupId,
            Integer cycleNumber,
            LocalDate cycleDate,
            LocalDateTime biddingStartTime,
            LocalDateTime biddingEndTime) {

        ChitGroup chitGroup =
                chitGroupRepository.findById(groupId)
                        .orElseThrow(() ->
                                new ChitGroupNotFoundException(
                                        "Chit group not found with id: "
                                                + groupId
                                )
                        );

        authorizationService.requireGroupOwner(chitGroup);

        /*
         * The OWNER's cycle drives the group lifecycle:
         *
         *   cycle created              -> group UPCOMING
         *   bidding start time arrives -> cycle BIDDING_OPEN, group ACTIVE
         *   last cycle paid out        -> group COMPLETED
         */
        if (chitGroup.getStatus() == ChitStatus.COMPLETED
                || chitGroup.getStatus() == ChitStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cycles cannot be created for a "
                            + chitGroup.getStatus() + " group.");
        }

        // The pot paid out each month is contribution x members, so the
        // group must be full or wallet money would be created from nothing.
        long memberCount =
                chitGroupMemberRepository.countByChitGroupId(groupId);

        if (memberCount != chitGroup.getMaxMembers()) {
            throw new BadRequestException(
                    "The group must be full before a cycle can start ("
                            + memberCount + " of "
                            + chitGroup.getMaxMembers()
                            + " members). Add the remaining members first.");
        }

        List<MonthlyCycle> existingCycles =
                monthlyCycleRepository.findByChitGroupId(groupId);

        boolean unfinished = existingCycles.stream().anyMatch(existing ->
                existing.getStatus() == CycleStatus.UPCOMING
                        || existing.getStatus() == CycleStatus.BIDDING_OPEN
                        || (existing.getStatus() == CycleStatus.COMPLETED
                        && existing.getWinner() != null
                        && !existing.isPayoutReleased()));

        if (unfinished) {
            throw new BadRequestException(
                    "Finish the current cycle first: bidding must end and "
                            + "you must distribute its payout before the "
                            + "next cycle is created.");
        }

        int expectedNumber =
                existingCycles.stream()
                        .mapToInt(MonthlyCycle::getCycleNumber)
                        .max()
                        .orElse(0) + 1;

        // Each member wins once, so there can never be more cycles than members.
        int totalCycles =
                Math.min(chitGroup.getDurationMonths(),
                        chitGroup.getMaxMembers());

        if (expectedNumber > totalCycles) {
            throw new BadRequestException(
                    "All " + totalCycles
                            + " cycles of this group have already been created.");
        }

        if (cycleNumber == null || cycleNumber != expectedNumber) {
            throw new BadRequestException(
                    "The next cycle number must be " + expectedNumber);
        }

        if (!biddingEndTime.isAfter(biddingStartTime)) {
            throw new BadRequestException(
                    "Bidding must end after it starts");
        }

        if (!biddingEndTime.isAfter(LocalDateTime.now())) {
            throw new BadRequestException(
                    "Bidding end time must be in the future");
        }

        if (monthlyCycleRepository
                .existsByChitGroupIdAndCycleNumber(
                        groupId,
                        cycleNumber)) {

            throw new RuntimeException(
                    "Cycle already exists for this chit group"
            );
        }

        MonthlyCycle cycle = new MonthlyCycle();

        cycle.setChitGroup(chitGroup);
        cycle.setCycleNumber(cycleNumber);
        cycle.setCycleDate(cycleDate);
        cycle.setBiddingStartTime(biddingStartTime);
        cycle.setBiddingEndTime(biddingEndTime);

        LocalDateTime now = LocalDateTime.now();

        // The end time is guaranteed to be in the future (checked above).
        cycle.setStatus(now.isBefore(biddingStartTime)
                ? CycleStatus.UPCOMING
                : CycleStatus.BIDDING_OPEN);

        MonthlyCycle saved = monthlyCycleRepository.save(cycle);

        // Create everyone's payment record now, so "Pay" works immediately
        // instead of failing with "Payment not found" until the scheduler runs.
        monthlyPaymentService.createPaymentsForCycle(saved);

        // Group follows the cycle: ACTIVE once bidding is open, otherwise
        // UPCOMING until the start time arrives (the scheduler activates it).
        if (saved.getStatus() == CycleStatus.BIDDING_OPEN) {
            chitGroup.setStatus(ChitStatus.ACTIVE);
        } else if (chitGroup.getStatus() == ChitStatus.DRAFT) {
            chitGroup.setStatus(ChitStatus.UPCOMING);
        }
        chitGroupRepository.save(chitGroup);

        auditService.log(com.clearchit.model.AuditAction.CYCLE_CREATED, "CHIT_GROUP", groupId, "Cycle " + cycleNumber + " created");
        return saved;
    }

    public List<MonthlyCycle> getCyclesByGroup(
            Long groupId) {

        if (!chitGroupRepository.existsById(groupId)) {

            throw new ChitGroupNotFoundException(
                    "Chit group not found with id: " + groupId
            );
        }

        return monthlyCycleRepository
                .findByChitGroupId(groupId);
    }

    public MonthlyCycle getCycleById(Long id) {

        return monthlyCycleRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Monthly cycle not found with id: "
                                        + id
                        )
                );
    }
}
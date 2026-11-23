package com.clearchit.controller;

import com.clearchit.dto.*;
import com.clearchit.model.Bid;
import com.clearchit.model.ChitGroup;
import com.clearchit.model.ChitGroupMember;
import com.clearchit.model.Member;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.model.MonthlyPayment;
import com.clearchit.model.Wallet;
import com.clearchit.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ClearChitController {

    private final ChitGroupService chitGroupService;
    private final MonthlyPaymentService monthlyPaymentService;
    private final MonthlyCycleService monthlyCycleService;
    private final BidService bidService;
    private final WalletService walletService;
    private final MemberService memberService;
    private final CycleSettlementService cycleSettlementService;
    private final AuditService auditService;
    private final LedgerService ledgerService;

    public ClearChitController(
            ChitGroupService chitGroupService,
            MemberService memberService,
            MonthlyCycleService monthlyCycleService,
            BidService bidService,
            WalletService walletService,
            MonthlyPaymentService monthlyPaymentService,
            CycleSettlementService cycleSettlementService,
            AuditService auditService,
            LedgerService ledgerService) {

        this.chitGroupService = chitGroupService;
        this.memberService = memberService;
        this.monthlyCycleService = monthlyCycleService;
        this.bidService = bidService;
        this.walletService = walletService;
        this.monthlyPaymentService = monthlyPaymentService;
        this.cycleSettlementService = cycleSettlementService;
        this.auditService = auditService;
        this.ledgerService = ledgerService;
    }

    // ------------------------------------------------
    // Health
    // ------------------------------------------------

    @GetMapping("/health")
    public String health() {
        return "ClearChit backend is running";
    }

    // ------------------------------------------------
    // Chit Group APIs
    // ------------------------------------------------

    @PostMapping("/chit-groups")
    public ChitGroup createChitGroup(
            @Valid @RequestBody ChitGroupRequest request) {

        return chitGroupService.createChitGroup(request);
    }

    @GetMapping("/chit-groups")
    public List<ChitGroup> getAllChitGroups() {

        return chitGroupService.getAllChitGroups();
    }

    @GetMapping("/chit-groups/{id}")
    public ChitGroup getChitGroupById(
            @PathVariable Long id) {

        return chitGroupService.getChitGroupById(id);
    }

    @PutMapping("/chit-groups/{id}")
    public ChitGroup updateChitGroup(
            @PathVariable Long id,
            @Valid @RequestBody ChitGroupRequest request) {

        return chitGroupService.updateChitGroup(
                id,
                request
        );
    }

    @DeleteMapping("/chit-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChitGroup(
            @PathVariable Long id) {

        chitGroupService.deleteChitGroup(id);
    }

    // ------------------------------------------------
    // Member APIs
    // ------------------------------------------------

    @PostMapping("/chit-groups/{groupId}/members/{memberId}")
    public ChitGroupMember addMemberToGroup(
            @PathVariable Long groupId,
            @PathVariable Long memberId) {

        return memberService.addMemberToGroup(
                groupId,
                memberId
        );
    }

    @DeleteMapping("/chit-groups/{groupId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMemberFromGroup(
            @PathVariable Long groupId,
            @PathVariable Long memberId) {

        memberService.removeMemberFromGroup(
                groupId,
                memberId
        );
    }

    @GetMapping("/chit-groups/{groupId}/members")
    public List<ChitGroupMember> getMembersByGroup(
            @PathVariable Long groupId) {

        return memberService.getMembersByGroup(groupId);
    }

    @GetMapping("/members/{memberId}/chit-groups")
    public List<MemberChitGroupResponse> getGroupsByMember(
            @PathVariable Long memberId) {

        return memberService.getGroupsByMember(memberId);
    }

    // ------------------------------------------------
    // Cycle APIs
    // ------------------------------------------------

    @PostMapping("/chit-groups/{groupId}/cycles")
    public MonthlyCycle createCycle(
            @PathVariable Long groupId,
            @RequestParam Integer cycleNumber,
            @RequestParam LocalDate cycleDate,
            @RequestParam LocalDateTime biddingStartTime,
            @RequestParam LocalDateTime biddingEndTime) {

        return monthlyCycleService.createCycle(
                groupId,
                cycleNumber,
                cycleDate,
                biddingStartTime,
                biddingEndTime
        );
    }

    @GetMapping("/chit-groups/{groupId}/cycles")
    public List<MonthlyCycle> getCyclesByGroup(
            @PathVariable Long groupId) {

        return monthlyCycleService.getCyclesByGroup(groupId);
    }

    // Owner presses "Distribute payout" after the winner is decided.
    @PostMapping("/cycles/{cycleId}/distribute-payout")
    public MonthlyCycle distributePayout(
            @PathVariable Long cycleId) {

        return cycleSettlementService.distributePayout(cycleId);
    }

    @GetMapping("/cycles/{id}")
    public MonthlyCycle getCycleById(
            @PathVariable Long id) {

        return monthlyCycleService.getCycleById(id);
    }

    // ------------------------------------------------
    // Bid APIs
    // ------------------------------------------------

    @PostMapping("/cycles/{cycleId}/bids")
    public Bid placeBid(
            @PathVariable Long cycleId,
            @Valid @RequestBody BidRequest request) {

        return bidService.placeBid(
                cycleId,
                request
        );
    }

    @GetMapping("/cycles/{cycleId}/bids")
    public List<BidResponse> getBidsByCycle(
            @PathVariable Long cycleId) {

        return bidService.getBidsByCycle(cycleId);
    }

    // ------------------------------------------------
    // Wallet APIs
    // ------------------------------------------------

    @GetMapping("/members/{memberId}/wallet")
    public Wallet getWallet(
            @PathVariable Long memberId) {

        return walletService.getWallet(memberId);
    }

    @PostMapping("/members/{memberId}/wallet/recharge")
    public Wallet rechargeWallet(
            @PathVariable Long memberId,
            @RequestParam BigDecimal amount) {

        return walletService.recharge(
                memberId,
                amount
        );
    }

    @PostMapping("/members/{memberId}/wallet/withdraw")
    public Wallet withdrawWallet(
            @PathVariable Long memberId,
            @RequestParam BigDecimal amount) {

        return walletService.withdraw(
                memberId,
                amount
        );
    }

    @GetMapping("/members/{memberId}/wallet/transactions")
    public List<WalletTransactionResponse> getWalletTransactions(
            @PathVariable Long memberId) {

        return walletService.getTransactions(
                memberId
        );
    }

    // ------------------------------------------------
    // Payment APIs
    // ------------------------------------------------

    @PostMapping("/cycles/{cycleId}/payments/{memberId}")
    public MonthlyPayment payContribution(
            @PathVariable Long cycleId,
            @PathVariable Long memberId) {

        return monthlyPaymentService.payContribution(
                cycleId,
                memberId
        );
    }

    @GetMapping("/cycles/{cycleId}/payments")
    public List<MonthlyPayment> getPaymentsByCycle(
            @PathVariable Long cycleId) {

        return monthlyPaymentService.getPaymentsForCycle(
                cycleId
        );
    }

    @GetMapping("/cycles/{cycleId}/payment-summary")
    public PaymentSummaryResponse getPaymentSummary(
            @PathVariable Long cycleId) {

        return monthlyPaymentService.getPaymentSummary(
                cycleId
        );
    }

    // ------------------------------------------------
    // Owner Test
    // ------------------------------------------------

    @GetMapping("/chit-groups/{groupId}/audit-logs")
    public List<com.clearchit.model.AuditLog> getAuditLogs(@PathVariable Long groupId) {
        // Authorization is enforced by checking that the caller is the owner or a member.
        return auditService.getGroupAuditLogs(groupId);
    }

    @GetMapping("/chit-groups/{groupId}/ledger")
    public List<WalletTransactionResponse> getGroupLedger(@PathVariable Long groupId) {
        return ledgerService.getGroupLedger(groupId);
    }

}
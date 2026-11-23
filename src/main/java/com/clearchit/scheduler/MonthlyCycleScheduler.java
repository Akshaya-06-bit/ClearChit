package com.clearchit.scheduler;

import com.clearchit.model.ChitStatus;
import com.clearchit.model.CycleStatus;
import com.clearchit.model.MonthlyCycle;
import com.clearchit.repository.ChitGroupRepository;
import com.clearchit.repository.MonthlyCycleRepository;
import com.clearchit.service.CycleFinalizationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Drives the life of every owner-created cycle:
 *
 *   UPCOMING     -> BIDDING_OPEN  when the bidding start time arrives
 *                                 (the group becomes ACTIVE at the same time)
 *   BIDDING_OPEN -> COMPLETED     when the bidding end time arrives
 *                                 (the winner is decided)
 *
 * Money is NOT moved here. After the winner is decided the group owner
 * distributes the payout with the "Distribute payout" button.
 */
@Component
public class MonthlyCycleScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(MonthlyCycleScheduler.class);

    private final MonthlyCycleRepository monthlyCycleRepository;
    private final ChitGroupRepository chitGroupRepository;
    private final CycleFinalizationService cycleFinalizationService;

    public MonthlyCycleScheduler(
            MonthlyCycleRepository monthlyCycleRepository,
            ChitGroupRepository chitGroupRepository,
            CycleFinalizationService cycleFinalizationService) {

        this.monthlyCycleRepository = monthlyCycleRepository;
        this.chitGroupRepository = chitGroupRepository;
        this.cycleFinalizationService = cycleFinalizationService;
    }

    @Scheduled(fixedDelay = 10000)
    public void processCycles() {

        LocalDateTime now = LocalDateTime.now();

        List<MonthlyCycle> cycles =
                monthlyCycleRepository.findByStatusIn(
                        List.of(CycleStatus.UPCOMING,
                                CycleStatus.BIDDING_OPEN));

        for (MonthlyCycle cycle : cycles) {

            // One failing cycle must not stop the others.
            try {
                processCycle(cycle, now);
            } catch (Exception exception) {
                log.error("Failed to process cycle {}",
                        cycle.getId(), exception);
            }
        }
    }

    private void processCycle(MonthlyCycle cycle, LocalDateTime now) {

        // Start time reached: open bidding.
        if (cycle.getStatus() == CycleStatus.UPCOMING
                && !now.isBefore(cycle.getBiddingStartTime())) {

            cycle.setStatus(CycleStatus.BIDDING_OPEN);
            monthlyCycleRepository.save(cycle);
        }

        if (cycle.getStatus() == CycleStatus.BIDDING_OPEN) {

            // An open cycle means the group is running.
            activateGroup(cycle.getChitGroup().getId());

            // End time reached: decide the winner.
            if (!now.isBefore(cycle.getBiddingEndTime())) {
                cycleFinalizationService.finalizeCycle(cycle);
            }
        }
    }

    private void activateGroup(Long groupId) {

        chitGroupRepository.findById(groupId).ifPresent(group -> {

            if (group.getStatus() == ChitStatus.DRAFT
                    || group.getStatus() == ChitStatus.UPCOMING) {

                group.setStatus(ChitStatus.ACTIVE);
                chitGroupRepository.save(group);
            }
        });
    }
}

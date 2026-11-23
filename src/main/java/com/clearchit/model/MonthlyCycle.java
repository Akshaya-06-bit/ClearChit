package com.clearchit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_cycles")
public class MonthlyCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "chit_group_id", nullable = false)
    private ChitGroup chitGroup;

    @Column(nullable = false)
    private Integer cycleNumber;

    @Column(nullable = false)
    private LocalDate cycleDate;

    @Column(nullable = false)
    private LocalDateTime biddingStartTime;

    @Column(nullable = false)
    private LocalDateTime biddingEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleStatus status;

    // Auction result

    @ManyToOne
    @JoinColumn(name = "winner_id")
    private Member winner;

    @Column(precision = 15, scale = 2)
    private BigDecimal winningBidAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(precision = 15, scale = 2)
    private BigDecimal commission;

    @Column(precision = 15, scale = 2)
    private BigDecimal dividendPool;

    @Column(precision = 15, scale = 2)
    private BigDecimal dividendPerMember;

    @Column(precision = 15, scale = 2)
    private BigDecimal payout;

    @Column(nullable = false)
    private boolean payoutReleased = false;

    public MonthlyCycle() {
    }


    public Long getId() {
        return id;
    }


    public ChitGroup getChitGroup() {
        return chitGroup;
    }

    public void setChitGroup(ChitGroup chitGroup) {
        this.chitGroup = chitGroup;
    }


    public Integer getCycleNumber() {
        return cycleNumber;
    }

    public void setCycleNumber(Integer cycleNumber) {
        this.cycleNumber = cycleNumber;
    }


    public LocalDate getCycleDate() {
        return cycleDate;
    }

    public void setCycleDate(LocalDate cycleDate) {
        this.cycleDate = cycleDate;
    }


    public LocalDateTime getBiddingStartTime() {
        return biddingStartTime;
    }

    public void setBiddingStartTime(LocalDateTime biddingStartTime) {
        this.biddingStartTime = biddingStartTime;
    }


    public LocalDateTime getBiddingEndTime() {
        return biddingEndTime;
    }

    public void setBiddingEndTime(LocalDateTime biddingEndTime) {
        this.biddingEndTime = biddingEndTime;
    }


    public CycleStatus getStatus() {
        return status;
    }

    public void setStatus(CycleStatus status) {
        this.status = status;
    }


    public Member getWinner() {
        return winner;
    }

    public void setWinner(Member winner) {
        this.winner = winner;
    }


    public BigDecimal getWinningBidAmount() {
        return winningBidAmount;
    }

    public void setWinningBidAmount(BigDecimal winningBidAmount) {
        this.winningBidAmount = winningBidAmount;
    }


    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }


    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }


    public BigDecimal getDividendPool() {
        return dividendPool;
    }

    public void setDividendPool(BigDecimal dividendPool) {
        this.dividendPool = dividendPool;
    }


    public BigDecimal getDividendPerMember() {
        return dividendPerMember;
    }

    public void setDividendPerMember(BigDecimal dividendPerMember) {
        this.dividendPerMember = dividendPerMember;
    }


    public BigDecimal getPayout() {
        return payout;
    }

    public void setPayout(BigDecimal payout) {
        this.payout = payout;
    }

    public boolean isPayoutReleased() {
        return payoutReleased;
    }

    public void setPayoutReleased(boolean payoutReleased) {
        this.payoutReleased = payoutReleased;
    }
}
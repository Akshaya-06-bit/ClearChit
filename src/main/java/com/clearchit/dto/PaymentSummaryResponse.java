package com.clearchit.dto;

import java.math.BigDecimal;

public class PaymentSummaryResponse {

    private Long cycleId;

    private long totalMembers;

    private long paidMembers;

    private long pendingMembers;

    private BigDecimal expectedCollection;

    private BigDecimal collectedAmount;

    private BigDecimal remainingAmount;

    private boolean collectionComplete;

    public PaymentSummaryResponse() {
    }

    public Long getCycleId() {
        return cycleId;
    }

    public void setCycleId(Long cycleId) {
        this.cycleId = cycleId;
    }

    public long getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(long totalMembers) {
        this.totalMembers = totalMembers;
    }

    public long getPaidMembers() {
        return paidMembers;
    }

    public void setPaidMembers(long paidMembers) {
        this.paidMembers = paidMembers;
    }

    public long getPendingMembers() {
        return pendingMembers;
    }

    public void setPendingMembers(long pendingMembers) {
        this.pendingMembers = pendingMembers;
    }

    public BigDecimal getExpectedCollection() {
        return expectedCollection;
    }

    public void setExpectedCollection(BigDecimal expectedCollection) {
        this.expectedCollection = expectedCollection;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public boolean isCollectionComplete() {
        return collectionComplete;
    }

    public void setCollectionComplete(boolean collectionComplete) {
        this.collectionComplete = collectionComplete;
    }
}
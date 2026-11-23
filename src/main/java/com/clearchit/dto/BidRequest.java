package com.clearchit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BidRequest {

    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Bid amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Bid amount must be greater than 0"
    )
    private BigDecimal bidAmount;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(BigDecimal bidAmount) {
        this.bidAmount = bidAmount;
    }
}
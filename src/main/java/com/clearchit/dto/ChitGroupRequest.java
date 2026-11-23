package com.clearchit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ChitGroupRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Chit value is required")
    @DecimalMin(
            value = "0.01",
            message = "Chit value must be greater than 0"
    )
    private BigDecimal chitValue;

    @NotNull(message = "Organiser commission rate is required")
    @DecimalMin(
            value = "0.0",
            message = "Commission rate cannot be negative"
    )
    @DecimalMax(
            value = "100.0",
            message = "Commission rate cannot exceed 100"
    )
    private BigDecimal organiserCommissionRate;

    @NotNull(message = "Monthly contribution is required")
    @DecimalMin(
            value = "0.01",
            message = "Monthly contribution must be greater than 0"
    )
    private BigDecimal monthlyContribution;

    @NotNull(message = "Maximum members is required")
    @Min(
            value = 2,
            message = "Maximum members must be at least 2"
    )
    private Integer maxMembers;

    @NotNull(message = "Duration is required")
    @Min(
            value = 1,
            message = "Duration must be at least 1 month"
    )
    private Integer durationMonths;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getChitValue() {
        return chitValue;
    }

    public void setChitValue(BigDecimal chitValue) {
        this.chitValue = chitValue;
    }

    public BigDecimal getOrganiserCommissionRate() {
        return organiserCommissionRate;
    }

    public void setOrganiserCommissionRate(
            BigDecimal organiserCommissionRate) {
        this.organiserCommissionRate = organiserCommissionRate;
    }

    public BigDecimal getMonthlyContribution() {
        return monthlyContribution;
    }

    public void setMonthlyContribution(BigDecimal monthlyContribution) {
        this.monthlyContribution = monthlyContribution;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(Integer maxMembers) {
        this.maxMembers = maxMembers;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public void setDurationMonths(Integer durationMonths) {
        this.durationMonths = durationMonths;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
}
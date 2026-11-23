package com.clearchit.dto;

import com.clearchit.model.ChitStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ChitGroupResponse {

    private Long id;
    private String name;
    private BigDecimal chitValue;
    private BigDecimal monthlyContribution;
    private BigDecimal organiserCommissionRate;
    private Integer maxMembers;
    private Integer durationMonths;
    private LocalDate startDate;
    private ChitStatus status;

    public ChitGroupResponse() {
    }

    public ChitGroupResponse(
            Long id,
            String name,
            BigDecimal chitValue,
            BigDecimal monthlyContribution,
            BigDecimal organiserCommissionRate,
            Integer maxMembers,
            Integer durationMonths,
            LocalDate startDate,
            ChitStatus status) {

        this.id = id;
        this.name = name;
        this.chitValue = chitValue;
        this.monthlyContribution = monthlyContribution;
        this.organiserCommissionRate = organiserCommissionRate;
        this.maxMembers = maxMembers;
        this.durationMonths = durationMonths;
        this.startDate = startDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getChitValue() {
        return chitValue;
    }

    public BigDecimal getMonthlyContribution() {
        return monthlyContribution;
    }

    public BigDecimal getOrganiserCommissionRate() {
        return organiserCommissionRate;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public ChitStatus getStatus() {
        return status;
    }
}
package com.clearchit.dto;

import com.clearchit.model.ChitStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MemberChitGroupResponse {

    private Long id;
    private String name;
    private BigDecimal chitValue;
    private BigDecimal monthlyContribution;
    private Integer maxMembers;
    private Integer durationMonths;
    private LocalDate startDate;
    private ChitStatus status;

    public MemberChitGroupResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public ChitStatus getStatus() {
        return status;
    }

    public void setStatus(ChitStatus status) {
        this.status = status;
    }
}


package com.clearchit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chit_groups")
public class ChitGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal chitValue;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyContribution;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal organiserCommissionRate;

    @Column(nullable = false)
    private Integer maxMembers;

    @Column(nullable = false)
    private Integer durationMonths;

    @Column(nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChitStatus status;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private Member owner;

    @OneToMany(mappedBy = "chitGroup")
    @JsonIgnore
    private List<ChitGroupMember> members = new ArrayList<>();

    public ChitGroup() {
    }

    public Long getId() {
        return id;
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

    public BigDecimal getOrganiserCommissionRate() {
        return organiserCommissionRate;
    }

    public void setOrganiserCommissionRate(
            BigDecimal organiserCommissionRate) {
        this.organiserCommissionRate = organiserCommissionRate;
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

    public Member getOwner() {
        return owner;
    }

    public void setOwner(Member owner) {
        this.owner = owner;
    }

    public List<ChitGroupMember> getMembers() {
        return members;
    }
}
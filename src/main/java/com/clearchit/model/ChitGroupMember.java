package com.clearchit.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Entity
@Table(
        name = "chit_group_members",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "chit_group_id",
                        "member_id"
                }
        )
)

public class ChitGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Many membership records can belong to one chit group.
     */
    @ManyToOne
    @JoinColumn(name = "chit_group_id", nullable = false)
    @JsonIgnore
    private ChitGroup chitGroup;

    /*
     * Many membership records can belong to one member.
     */
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)

    private Member member;

    public ChitGroupMember() {
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

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }
}
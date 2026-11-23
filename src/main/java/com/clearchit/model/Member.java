package com.clearchit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /*
     * One member can belong to many chit groups.
     *
     * Each membership is represented by ChitGroupMember.
     */
    @OneToMany(mappedBy = "member")
    @JsonIgnore
    private List<ChitGroupMember> memberships = new ArrayList<>();

    public Member() {
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<ChitGroupMember> getMemberships() {
        return memberships;
    }
}
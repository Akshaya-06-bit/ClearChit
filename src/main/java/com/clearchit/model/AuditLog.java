package com.clearchit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_group", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false) private String actorEmail;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AuditAction action;
    @Column(nullable=false) private String entityType;
    @Column(nullable=false) private Long entityId;
    @Column(length=2000) private String details;
    @Column(nullable=false) private LocalDateTime createdAt;

    public Long getId(){return id;}
    public String getActorEmail(){return actorEmail;}
    public void setActorEmail(String v){actorEmail=v;}
    public AuditAction getAction(){return action;}
    public void setAction(AuditAction v){action=v;}
    public String getEntityType(){return entityType;}
    public void setEntityType(String v){entityType=v;}
    public Long getEntityId(){return entityId;}
    public void setEntityId(Long v){entityId=v;}
    public String getDetails(){return details;}
    public void setDetails(String v){details=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime v){createdAt=v;}
}

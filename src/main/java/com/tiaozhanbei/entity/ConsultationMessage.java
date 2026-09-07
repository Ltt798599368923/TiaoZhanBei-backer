package com.tiaozhanbei.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_consultation_message")
public class ConsultationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "read_by_user")
    private Boolean readByUser = false;

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) createdTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConsultationId() { return consultationId; }
    public void setConsultationId(Long consultationId) { this.consultationId = consultationId; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public Boolean getReadByUser() { return readByUser; }
    public void setReadByUser(Boolean readByUser) { this.readByUser = readByUser; }
}

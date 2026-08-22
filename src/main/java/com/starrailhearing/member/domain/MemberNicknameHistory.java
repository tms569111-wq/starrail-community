package com.starrailhearing.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_nickname_history")
public class MemberNicknameHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Column(name = "previous_nickname", nullable = false, length = 50)
    private String previousNickname;

    @Column(name = "changed_nickname", nullable = false, length = 50)
    private String changedNickname;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected MemberNicknameHistory() {
    }

    public MemberNicknameHistory(
            MemberAccount member,
            String previousNickname,
            String changedNickname,
            LocalDateTime changedAt
    ) {
        this.member = member;
        this.previousNickname = previousNickname;
        this.changedNickname = changedNickname;
        this.changedAt = changedAt;
    }

    public String getPreviousNickname() { return previousNickname; }
    public String getChangedNickname() { return changedNickname; }
    public LocalDateTime getChangedAt() { return changedAt; }
}

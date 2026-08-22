package com.starrailhearing.comment.domain;

import com.starrailhearing.member.domain.MemberAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "comment_like", uniqueConstraints = @UniqueConstraint(
        name = "uq_comment_like_member_comment", columnNames = {"member_id", "comment_id"}
))
public class CommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private CharacterComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CommentLike() {
    }

    public CommentLike(CharacterComment comment, MemberAccount member) {
        this.comment = comment;
        this.member = member;
    }

    @PrePersist
    private void setCreatedAt() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}

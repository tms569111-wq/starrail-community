package com.starrailhearing.comment.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_comment")
public class CharacterComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private CharacterEvaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_character_id")
    private VerifiedCharacter verifiedCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private CharacterComment parent;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "eidolon_at_write", nullable = false)
    private int eidolonAtWrite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected CharacterComment() {
    }

    public CharacterComment(
            MemberAccount member,
            CharacterEvaluation evaluation,
            VerifiedCharacter verifiedCharacter,
            String content
    ) {
        this.member = member;
        this.evaluation = evaluation;
        this.verifiedCharacter = verifiedCharacter;
        this.eidolonAtWrite = verifiedCharacter.getEidolon();
        this.content = normalizeContent(content);
        this.status = CommentStatus.ACTIVE;
        this.likeCount = 0;
    }

    public CharacterComment(
            MemberAccount member,
            CharacterEvaluation evaluation,
            VerifiedCharacter verifiedCharacter,
            CharacterComment parent,
            String content
    ) {
        if (parent == null || parent.parent != null) {
            throw new IllegalArgumentException("답글은 최상위 댓글에만 작성할 수 있습니다.");
        }
        if (!parent.evaluation.getId().equals(evaluation.getId())) {
            throw new IllegalArgumentException("다른 캐릭터의 댓글에는 답글을 작성할 수 없습니다.");
        }
        this.member = member;
        this.evaluation = evaluation;
        this.verifiedCharacter = verifiedCharacter;
        this.parent = parent;
        this.eidolonAtWrite = verifiedCharacter.getEidolon();
        this.content = normalizeContent(content);
        this.status = CommentStatus.ACTIVE;
        this.likeCount = 0;
    }

    public void edit(String content, VerifiedCharacter verifiedCharacter) {
        this.content = normalizeContent(content);
        this.verifiedCharacter = verifiedCharacter;
        this.eidolonAtWrite = verifiedCharacter.getEidolon();
    }

    public void deleteByAuthor(LocalDateTime now) {
        status = CommentStatus.DELETED_BY_AUTHOR;
        content = "삭제된 댓글입니다.";
        deletedAt = now;
    }

    public void hideByModerator(LocalDateTime now) {
        status = CommentStatus.HIDDEN_BY_MODERATOR;
        deletedAt = now;
    }

    public void restoreByModerator() {
        if (status != CommentStatus.HIDDEN_BY_MODERATOR) {
            throw new IllegalStateException("운영자가 숨긴 댓글만 복원할 수 있습니다.");
        }
        status = CommentStatus.ACTIVE;
        deletedAt = null;
    }

    private String normalizeContent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 1000) {
            throw new IllegalArgumentException("댓글은 1~1000자여야 합니다.");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public MemberAccount getMember() {
        return member;
    }

    public CharacterEvaluation getEvaluation() {
        return evaluation;
    }

    public String getContent() {
        return content;
    }

    public int getEidolonAtWrite() {
        return eidolonAtWrite;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public CharacterComment getParent() {
        return parent;
    }

    public boolean isRoot() {
        return parent == null;
    }
}

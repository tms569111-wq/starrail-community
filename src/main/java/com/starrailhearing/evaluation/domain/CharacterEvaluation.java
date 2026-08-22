package com.starrailhearing.evaluation.domain;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.common.domain.BaseTimeEntity;
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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_evaluation", uniqueConstraints = @UniqueConstraint(
        name = "uq_evaluation_character_version", columnNames = {"character_id", "version_id"}
))
public class CharacterEvaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private GameVersion version;

    @Column(name = "tier_copy_json", columnDefinition = "LONGTEXT")
    private String tierCopyJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected CharacterEvaluation() {
    }

    public CharacterEvaluation(
            GameCharacter character,
            GameVersion version,
            String tierCopyJson,
            LocalDateTime openedAt
    ) {
        this.character = character;
        this.version = version;
        this.tierCopyJson = tierCopyJson;
        this.status = EvaluationStatus.OPEN;
        this.openedAt = openedAt;
    }

    public void close(LocalDateTime now) {
        status = EvaluationStatus.CLOSED;
        closedAt = now;
    }

    public Long getId() {
        return id;
    }

    public GameCharacter getCharacter() {
        return character;
    }

    public String getGameVersion() {
        return version.getVersionCode();
    }

    public GameVersion getVersion() { return version; }
    public String getTierCopyJson() { return tierCopyJson; }
    public EvaluationStatus getStatus() { return status; }
}

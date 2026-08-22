package com.starrailhearing.profile.domain;

import com.starrailhearing.character.domain.GameCharacter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "verified_character", uniqueConstraints = @UniqueConstraint(
        name = "uq_verified_character_profile_character", columnNames = {"profile_id", "character_id"}
))
public class VerifiedCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private GameProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    @Column(nullable = false)
    private int eidolon;

    @Column(name = "first_verified_at", nullable = false)
    private LocalDateTime firstVerifiedAt;

    @Column(name = "last_verified_at", nullable = false)
    private LocalDateTime lastVerifiedAt;

    protected VerifiedCharacter() {
    }

    public VerifiedCharacter(
            GameProfile profile,
            GameCharacter character,
            int eidolon,
            LocalDateTime verifiedAt
    ) {
        validateEidolon(eidolon);
        this.profile = profile;
        this.character = character;
        this.eidolon = eidolon;
        this.firstVerifiedAt = verifiedAt;
        this.lastVerifiedAt = verifiedAt;
    }

    public void refresh(int observedEidolon, LocalDateTime verifiedAt) {
        validateEidolon(observedEidolon);
        eidolon = Math.max(eidolon, observedEidolon);
        lastVerifiedAt = verifiedAt;
    }

    private void validateEidolon(int value) {
        if (value < 0 || value > 6) {
            throw new IllegalArgumentException("성혼은 0~6 사이여야 합니다.");
        }
    }

    public Long getId() {
        return id;
    }

    public GameProfile getProfile() {
        return profile;
    }

    public GameCharacter getCharacter() {
        return character;
    }

    public int getEidolon() {
        return eidolon;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }
}

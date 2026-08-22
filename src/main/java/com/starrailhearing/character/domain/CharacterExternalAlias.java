package com.starrailhearing.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "character_external_alias", uniqueConstraints = @UniqueConstraint(
        name = "uq_character_alias_provider_external", columnNames = {"provider", "external_id"}
))
public class CharacterExternalAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileProvider provider;

    @Column(name = "external_id", nullable = false, length = 20)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private GameCharacter character;

    protected CharacterExternalAlias() {
    }

    public CharacterExternalAlias(ProfileProvider provider, String externalId, GameCharacter character) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.externalId = requireExternalId(externalId);
        this.character = java.util.Objects.requireNonNull(character);
    }

    public void changeCharacter(GameCharacter character) {
        this.character = java.util.Objects.requireNonNull(character);
    }

    private String requireExternalId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("외부 캐릭터 ID를 확인해 주세요.");
        }
        return normalized;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getId() { return id; }
    public ProfileProvider getProvider() { return provider; }

    public GameCharacter getCharacter() {
        return character;
    }
}

package com.starrailhearing.character.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "game_character", uniqueConstraints = {
        @UniqueConstraint(name = "uq_character_external_id", columnNames = "canonical_external_id"),
        @UniqueConstraint(name = "uq_character_slug", columnNames = "slug")
})
public class GameCharacter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_external_id", nullable = false, length = 20)
    private String canonicalExternalId;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int rarity;

    @Column(name = "path_code", nullable = false, length = 30)
    private String pathCode;

    @Column(name = "path_name", nullable = false, length = 30)
    private String pathName;

    @Column(name = "element_code", nullable = false, length = 30)
    private String elementCode;

    @Column(name = "element_name", nullable = false, length = 30)
    private String elementName;

    @Column(name = "icon_url", nullable = false, length = 500)
    private String iconUrl;

    @Column(name = "portrait_url", nullable = false, length = 500)
    private String portraitUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CharacterStatus status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected GameCharacter() {
    }

    public GameCharacter(
            String canonicalExternalId,
            String slug,
            String name,
            int rarity,
            String pathCode,
            String pathName,
            String elementCode,
            String elementName,
            String iconUrl,
            String portraitUrl,
            int displayOrder
    ) {
        update(canonicalExternalId, slug, name, rarity, pathCode, pathName, elementCode,
                elementName, iconUrl, portraitUrl, displayOrder);
        this.status = CharacterStatus.ACTIVE;
    }

    public void update(
            String canonicalExternalId,
            String slug,
            String name,
            int rarity,
            String pathCode,
            String pathName,
            String elementCode,
            String elementName,
            String iconUrl,
            String portraitUrl,
            int displayOrder
    ) {
        this.canonicalExternalId = required(canonicalExternalId, 20, "기준 외부 ID");
        if (!this.canonicalExternalId.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("기준 외부 ID는 숫자여야 합니다.");
        }
        this.slug = validateSlug(slug);
        this.name = required(name, 100, "캐릭터 이름");
        if (rarity != 4 && rarity != 5) throw new IllegalArgumentException("희귀도는 4 또는 5여야 합니다.");
        this.rarity = rarity;
        this.pathCode = required(pathCode, 30, "운명의 길 코드");
        this.pathName = required(pathName, 30, "운명의 길 이름");
        this.elementCode = required(elementCode, 30, "속성 코드");
        this.elementName = required(elementName, 30, "속성 이름");
        this.iconUrl = validateUrl(iconUrl, "아이콘 URL");
        this.portraitUrl = validateUrl(portraitUrl, "전신 이미지 URL");
        this.displayOrder = Math.max(0, displayOrder);
    }

    public void hide() { status = CharacterStatus.HIDDEN; }
    public void show() { status = CharacterStatus.ACTIVE; }

    private String required(String value, int maximum, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String validateSlug(String value) {
        String normalized = required(value, 100, "슬러그");
        if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("슬러그는 영문 소문자, 숫자와 가운데 하이픈만 허용합니다.");
        }
        return normalized;
    }

    private String validateUrl(String value, String field) {
        String normalized = required(value, 500, field);
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException(field + "은 http(s) URL이어야 합니다.");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + "을 확인해 주세요.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCanonicalExternalId() {
        return canonicalExternalId;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public int getRarity() {
        return rarity;
    }

    public String getPathCode() {
        return pathCode;
    }

    public String getPathName() {
        return pathName;
    }

    public String getElementCode() {
        return elementCode;
    }

    public String getElementName() {
        return elementName;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getPortraitUrl() {
        return portraitUrl;
    }

    public CharacterStatus getStatus() { return status; }
    public int getDisplayOrder() { return displayOrder; }
}

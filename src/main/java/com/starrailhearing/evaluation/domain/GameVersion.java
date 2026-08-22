package com.starrailhearing.evaluation.domain;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "game_version", uniqueConstraints = @UniqueConstraint(
        name = "uq_game_version_code", columnNames = "version_code"
))
public class GameVersion extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, length = 20)
    private String versionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status;

    @Column(name = "minimum_sample", nullable = false)
    private int minimumSample;

    @Column(name = "rules_snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rulesSnapshotJson;

    @Column(name = "archive_json", columnDefinition = "LONGTEXT")
    private String archiveJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_status", nullable = false, length = 20)
    private AggregationStatus aggregationStatus;

    @Column(name = "last_aggregated_at")
    private LocalDateTime lastAggregatedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected GameVersion() {
    }

    public GameVersion(String versionCode, int minimumSample, String rulesSnapshotJson) {
        String normalized = versionCode == null ? "" : versionCode.trim();
        if (!normalized.matches("[0-9]+(\\.[0-9]+){1,2}")) {
            throw new IllegalArgumentException("게임 버전은 4.4 또는 4.4.1 형식이어야 합니다.");
        }
        if (minimumSample < 1 || minimumSample > 10000) {
            throw new IllegalArgumentException("최소 표본 수를 확인해 주세요.");
        }
        this.versionCode = normalized;
        this.minimumSample = minimumSample;
        this.rulesSnapshotJson = requireRules(rulesSnapshotJson);
        this.status = VersionStatus.DRAFT;
        this.aggregationStatus = AggregationStatus.WAITING;
    }

    public void open(String snapshotJson, int sample, LocalDateTime now) {
        requireStatus(VersionStatus.DRAFT);
        rulesSnapshotJson = requireRules(snapshotJson);
        minimumSample = sample;
        status = VersionStatus.OPEN;
        openedAt = now;
        aggregationStatus = AggregationStatus.WAITING;
    }

    public void startClosing() {
        requireStatus(VersionStatus.OPEN);
        status = VersionStatus.CLOSING;
    }

    public void close(String archiveJson, LocalDateTime now) {
        requireStatus(VersionStatus.CLOSING);
        this.archiveJson = requireRules(archiveJson);
        this.status = VersionStatus.CLOSED;
        this.closedAt = now;
        this.aggregationStatus = AggregationStatus.SUCCESS;
        this.lastAggregatedAt = now;
    }

    public void aggregationSucceeded(LocalDateTime now) {
        aggregationStatus = AggregationStatus.SUCCESS;
        lastAggregatedAt = now;
    }

    public void aggregationFailed() { aggregationStatus = AggregationStatus.FAILED; }

    private void requireStatus(VersionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("게임 버전 상태가 " + expected + "가 아닙니다.");
        }
    }

    private String requireRules(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("티어 규칙 스냅샷은 필수입니다.");
        return normalized;
    }

    public Long getId() { return id; }
    public String getVersionCode() { return versionCode; }
    public VersionStatus getStatus() { return status; }
    public int getMinimumSample() { return minimumSample; }
    public String getRulesSnapshotJson() { return rulesSnapshotJson; }
    public String getArchiveJson() { return archiveJson; }
    public AggregationStatus getAggregationStatus() { return aggregationStatus; }
    public LocalDateTime getLastAggregatedAt() { return lastAggregatedAt; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}

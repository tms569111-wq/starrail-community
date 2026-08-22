package com.starrailhearing.vote.domain;

import com.starrailhearing.evaluation.domain.CharacterEvaluation;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tier_aggregate", uniqueConstraints = @UniqueConstraint(
        name = "uq_tier_aggregate_evaluation_filter",
        columnNames = {"evaluation_id", "filter_code"}
))
public class TierAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private CharacterEvaluation evaluation;

    @Column(name = "filter_code", nullable = false, length = 20)
    private String filterCode;

    @Column(name = "vote_count", nullable = false)
    private long voteCount;

    @Column(name = "average_score", precision = 8, scale = 4)
    private BigDecimal averageScore;

    @Column(name = "tier_label", nullable = false, length = 20)
    private String tierLabel;

    @Column(name = "sample_sufficient", nullable = false)
    private boolean sampleSufficient;

    @Column(name = "t0_count", nullable = false)
    private long t0Count;

    @Column(name = "t05_count", nullable = false)
    private long t05Count;

    @Column(name = "t1_count", nullable = false)
    private long t1Count;

    @Column(name = "t15_count", nullable = false)
    private long t15Count;

    @Column(name = "t2_count", nullable = false)
    private long t2Count;

    @Column(name = "aggregated_at", nullable = false)
    private LocalDateTime aggregatedAt;

    protected TierAggregate() {
    }

    public CharacterEvaluation getEvaluation() { return evaluation; }
    public String getFilterCode() { return filterCode; }
    public long getVoteCount() { return voteCount; }
    public double getAverageScore() { return averageScore == null ? 0.0 : averageScore.doubleValue(); }
    public String getTierLabel() { return tierLabel; }
    public boolean isSampleSufficient() { return sampleSufficient; }
    public LocalDateTime getAggregatedAt() { return aggregatedAt; }

    public long countForOption(String code) {
        return switch (code) {
            case "T0" -> t0Count;
            case "T05" -> t05Count;
            case "T1" -> t1Count;
            case "T15" -> t15Count;
            case "T2" -> t2Count;
            default -> 0L;
        };
    }
}

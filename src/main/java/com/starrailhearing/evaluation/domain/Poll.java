package com.starrailhearing.evaluation.domain;

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
@Table(name = "poll", uniqueConstraints = @UniqueConstraint(
        name = "uq_poll_evaluation_type", columnNames = {"evaluation_id", "type"}
))
public class Poll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private CharacterEvaluation evaluation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PollType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PollStatus status;

    protected Poll() {
    }

    public Poll(CharacterEvaluation evaluation, PollType type, String title) {
        this.evaluation = evaluation;
        this.type = type;
        this.title = title;
        this.status = PollStatus.OPEN;
    }

    public void close() { status = PollStatus.CLOSED; }

    public Long getId() {
        return id;
    }

    public CharacterEvaluation getEvaluation() {
        return evaluation;
    }

    public String getTitle() {
        return title;
    }

    public PollStatus getStatus() { return status; }
}

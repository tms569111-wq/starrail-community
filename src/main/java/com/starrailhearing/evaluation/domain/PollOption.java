package com.starrailhearing.evaluation.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
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

@Entity
@Table(name = "poll_option", uniqueConstraints = {
        @UniqueConstraint(name = "uq_poll_option_code", columnNames = {"poll_id", "code"}),
        @UniqueConstraint(name = "uq_poll_option_order", columnNames = {"poll_id", "display_order"})
})
public class PollOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private int score;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PollOption() {
    }

    public PollOption(
            Poll poll,
            String code,
            String label,
            String description,
            int score,
            int displayOrder
    ) {
        this.poll = poll;
        this.code = code;
        this.label = label;
        this.description = description;
        this.score = score;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Poll getPoll() {
        return poll;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getScore() {
        return score;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}

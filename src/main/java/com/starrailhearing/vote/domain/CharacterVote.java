package com.starrailhearing.vote.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollOption;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.profile.domain.VerifiedCharacter;
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
@Table(name = "character_vote", uniqueConstraints = @UniqueConstraint(
        name = "uq_character_vote_member_poll", columnNames = {"member_id", "poll_id"}
))
public class CharacterVote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private PollOption option;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_character_id")
    private VerifiedCharacter verifiedCharacter;

    @Column(name = "eidolon_at_vote", nullable = false)
    private int eidolonAtVote;

    protected CharacterVote() {
    }

    public CharacterVote(
            MemberAccount member,
            Poll poll,
            PollOption option,
            VerifiedCharacter verifiedCharacter
    ) {
        this.member = member;
        this.poll = poll;
        update(option, verifiedCharacter);
    }

    public void update(PollOption option, VerifiedCharacter verifiedCharacter) {
        this.option = option;
        this.verifiedCharacter = verifiedCharacter;
        this.eidolonAtVote = verifiedCharacter.getEidolon();
    }

    public Long getId() {
        return id;
    }

    public PollOption getOption() {
        return option;
    }

    public int getEidolonAtVote() {
        return eidolonAtVote;
    }
}

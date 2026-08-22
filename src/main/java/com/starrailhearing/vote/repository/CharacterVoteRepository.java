package com.starrailhearing.vote.repository;

import com.starrailhearing.vote.domain.CharacterVote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CharacterVoteRepository extends JpaRepository<CharacterVote, Long> {

    @EntityGraph(attributePaths = "option")
    Optional<CharacterVote> findByMember_IdAndPoll_Id(Long memberId, Long pollId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO character_vote
            (member_id, poll_id, option_id, verified_character_id, eidolon_at_vote, created_at, updated_at)
            VALUES (:memberId, :pollId, :optionId, :verifiedCharacterId, :eidolon, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE
                option_id = VALUES(option_id),
                verified_character_id = VALUES(verified_character_id),
                eidolon_at_vote = VALUES(eidolon_at_vote),
                updated_at = NOW(6)
            """, nativeQuery = true)
    int upsertVote(
            @Param("memberId") Long memberId,
            @Param("pollId") Long pollId,
            @Param("optionId") Long optionId,
            @Param("verifiedCharacterId") Long verifiedCharacterId,
            @Param("eidolon") int eidolon
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from CharacterVote vote where vote.member.id = :memberId")
    int deleteByMember_Id(@Param("memberId") Long memberId);
}

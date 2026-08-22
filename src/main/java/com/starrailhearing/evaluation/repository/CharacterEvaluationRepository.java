package com.starrailhearing.evaluation.repository;

import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.EvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CharacterEvaluationRepository extends JpaRepository<CharacterEvaluation, Long> {
    Optional<CharacterEvaluation> findFirstByCharacter_IdAndStatusOrderByOpenedAtDesc(
            Long characterId,
            EvaluationStatus status
    );

    List<CharacterEvaluation> findAllByVersion_IdOrderByCharacter_DisplayOrderAsc(Long versionId);

    Optional<CharacterEvaluation> findByCharacter_IdAndVersion_Id(Long characterId, Long versionId);
}

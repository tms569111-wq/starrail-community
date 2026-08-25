package com.starrailhearing.evaluation.repository;

import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CharacterEvaluationRepository extends JpaRepository<CharacterEvaluation, Long> {
    List<CharacterEvaluation> findAllByVersion_IdOrderByCharacter_DisplayOrderAsc(Long versionId);

    Optional<CharacterEvaluation> findByCharacter_IdAndVersion_Id(Long characterId, Long versionId);
}

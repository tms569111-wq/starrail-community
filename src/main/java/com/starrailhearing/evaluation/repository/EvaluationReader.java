package com.starrailhearing.evaluation.repository;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollType;
import org.springframework.stereotype.Component;

@Component
public class EvaluationReader {

    private final CharacterEvaluationRepository evaluationRepository;
    private final PollRepository pollRepository;

    public EvaluationReader(
            CharacterEvaluationRepository evaluationRepository,
            PollRepository pollRepository
    ) {
        this.evaluationRepository = evaluationRepository;
        this.pollRepository = pollRepository;
    }

    public CharacterEvaluation requireEvaluation(Long characterId, Long versionId) {
        return evaluationRepository.findByCharacter_IdAndVersion_Id(characterId, versionId)
                .orElseThrow(() -> new AppException(ErrorCode.EVALUATION_NOT_FOUND));
    }

    public Poll requireTierPoll(CharacterEvaluation evaluation) {
        return pollRepository.findByEvaluation_IdAndType(evaluation.getId(), PollType.TIER)
                .orElseThrow(() -> new AppException(ErrorCode.POLL_NOT_FOUND));
    }
}

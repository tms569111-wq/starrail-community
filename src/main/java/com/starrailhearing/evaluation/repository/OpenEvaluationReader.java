package com.starrailhearing.evaluation.repository;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.EvaluationStatus;
import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollStatus;
import com.starrailhearing.evaluation.domain.PollType;
import org.springframework.stereotype.Component;

@Component
public class OpenEvaluationReader {

    private final CharacterEvaluationRepository evaluationRepository;
    private final PollRepository pollRepository;

    public OpenEvaluationReader(
            CharacterEvaluationRepository evaluationRepository,
            PollRepository pollRepository
    ) {
        this.evaluationRepository = evaluationRepository;
        this.pollRepository = pollRepository;
    }

    public CharacterEvaluation requireEvaluation(Long characterId) {
        return evaluationRepository
                .findFirstByCharacter_IdAndStatusOrderByOpenedAtDesc(characterId, EvaluationStatus.OPEN)
                .orElseThrow(() -> new AppException(ErrorCode.EVALUATION_NOT_FOUND));
    }

    public Poll requireTierPoll(CharacterEvaluation evaluation) {
        return pollRepository.findByEvaluation_IdAndTypeAndStatus(
                        evaluation.getId(), PollType.TIER, PollStatus.OPEN
                )
                .orElseThrow(() -> new AppException(ErrorCode.POLL_NOT_FOUND));
    }
}

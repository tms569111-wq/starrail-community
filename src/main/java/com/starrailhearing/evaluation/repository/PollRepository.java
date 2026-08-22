package com.starrailhearing.evaluation.repository;

import com.starrailhearing.evaluation.domain.Poll;
import com.starrailhearing.evaluation.domain.PollStatus;
import com.starrailhearing.evaluation.domain.PollType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {
    Optional<Poll> findByEvaluation_IdAndTypeAndStatus(
            Long evaluationId,
            PollType type,
            PollStatus status
    );

    List<Poll> findAllByEvaluation_Version_Id(Long versionId);
}

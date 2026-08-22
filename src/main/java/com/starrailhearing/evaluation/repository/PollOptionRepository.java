package com.starrailhearing.evaluation.repository;

import com.starrailhearing.evaluation.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {
    List<PollOption> findByPoll_IdOrderByDisplayOrderAsc(Long pollId);

    Optional<PollOption> findByIdAndPoll_Id(Long optionId, Long pollId);
}

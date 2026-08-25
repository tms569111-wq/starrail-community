package com.starrailhearing.evaluation.repository;

import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameVersionRepository extends JpaRepository<GameVersion, Long> {
    Optional<GameVersion> findByVersionCode(String versionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from GameVersion version where version.id = :id")
    Optional<GameVersion> findForUpdateById(@Param("id") Long id);
    Optional<GameVersion> findFirstByStatusOrderByOpenedAtDesc(VersionStatus status);
    Optional<GameVersion> findFirstByStatusOrderByCreatedAtDesc(VersionStatus status);
    Optional<GameVersion> findFirstByStatusOrderByClosedAtDesc(VersionStatus status);
    boolean existsByStatusIn(List<VersionStatus> statuses);
    List<GameVersion> findAllByOrderByCreatedAtDesc();
    List<GameVersion> findAllByStatusIn(List<VersionStatus> statuses);
    List<GameVersion> findAllByStatusInOrderByCreatedAtDesc(List<VersionStatus> statuses);
}

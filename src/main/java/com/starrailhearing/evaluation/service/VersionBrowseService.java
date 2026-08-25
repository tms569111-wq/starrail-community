package com.starrailhearing.evaluation.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class VersionBrowseService {

    private static final List<VersionStatus> VISIBLE_STATUSES = List.of(
            VersionStatus.OPEN,
            VersionStatus.CLOSING,
            VersionStatus.CLOSED
    );

    private final GameVersionRepository versionRepository;

    public VersionBrowseService(GameVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    public Optional<GameVersion> findSelected(String rawVersion) {
        String versionCode = normalize(rawVersion);
        if (!versionCode.isBlank()) {
            GameVersion version = versionRepository.findByVersionCode(versionCode)
                    .filter(candidate -> VISIBLE_STATUSES.contains(candidate.getStatus()))
                    .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
            return Optional.of(version);
        }
        return defaultVersion();
    }

    public GameVersion requireSelected(String rawVersion) {
        return findSelected(rawVersion)
                .orElseThrow(() -> new AppException(ErrorCode.VERSION_NOT_FOUND));
    }

    public List<VersionOptionView> options() {
        return versionRepository.findAllByStatusInOrderByCreatedAtDesc(VISIBLE_STATUSES).stream()
                .map(version -> new VersionOptionView(
                        version.getVersionCode(),
                        version.getStatus(),
                        statusLabel(version.getStatus())
                ))
                .toList();
    }

    private Optional<GameVersion> defaultVersion() {
        return versionRepository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN)
                .or(() -> versionRepository.findFirstByStatusOrderByCreatedAtDesc(
                        VersionStatus.CLOSING
                ))
                .or(() -> versionRepository.findFirstByStatusOrderByClosedAtDesc(
                        VersionStatus.CLOSED
                ));
    }

    private String statusLabel(VersionStatus status) {
        return switch (status) {
            case OPEN -> "진행 중";
            case CLOSING -> "종료 처리 중";
            case CLOSED -> "종료";
            case DRAFT -> "준비 중";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.common.web.PagedView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class TitleVerificationService {
    private static final Logger log = LoggerFactory.getLogger(TitleVerificationService.class);

    private final TitleImageStorage storage;
    private final TitleRequestPersistenceService persistenceService;

    public TitleVerificationService(
            TitleImageStorage storage,
            TitleRequestPersistenceService persistenceService
    ) {
        this.storage = storage;
        this.persistenceService = persistenceService;
    }

    public long submit(long memberId, String version, String tier, MultipartFile file) {
        var submission = persistenceService.validateSubmissionBeforeUpload(memberId, version, tier);
        TitleImageStorage.StoredTitleImage image = storage.store(file);
        try {
            return persistenceService.create(
                    memberId, submission.version(), submission.badgeType(), image
            );
        } catch (RuntimeException exception) {
            storage.delete(image.path());
            throw exception;
        }
    }

    public List<TitleRequestPersistenceService.TitleApplicationVersionView> applicationVersions() {
        return persistenceService.applicationVersions();
    }

    public List<TitleRequestPersistenceService.TitleApplicationTierView> applicationTiers() {
        return persistenceService.applicationTiers();
    }

    public List<TitleRequestView> memberViews(long memberId) {
        return persistenceService.memberViews(memberId);
    }

    public boolean hasVerifiedProfile(long memberId) {
        return persistenceService.hasVerifiedProfile(memberId);
    }

    public PagedView<TitleRequestView> adminViews(long operatorId, int page) {
        return persistenceService.adminViews(operatorId, page);
    }

    public ImageContent image(long operatorId, long requestId) {
        var descriptor = persistenceService.requireImage(operatorId, requestId);
        Resource resource = storage.load(descriptor.path());
        return new ImageContent(resource, descriptor.mimeType());
    }

    public void decide(long operatorId, long requestId, boolean approve, String note) {
        deleteAndClear(persistenceService.decide(operatorId, requestId, approve, note));
    }

    public void cancel(long memberId, long requestId) {
        if (!deleteAndClear(persistenceService.cancel(memberId, requestId))) {
            throw new AppException(
                    ErrorCode.SERVICE_BUSY,
                    "신청 취소는 접수됐지만 파일 정리가 진행 중입니다. 잠시 후 다시 확인해 주세요."
            );
        }
    }

    @Scheduled(initialDelayString = "1m", fixedDelayString = "1h")
    public void expirePending() {
        persistenceService.expirePending().forEach(this::deleteAndClear);
        persistenceService.pendingEvidenceCleanup().forEach(this::deleteAndClear);
    }

    private boolean deleteAndClear(TitleRequestPersistenceService.EvidenceCleanup cleanup) {
        try {
            if (storage.delete(cleanup.path())) {
                persistenceService.clearEvidence(cleanup.requestId(), cleanup.path());
                return true;
            } else {
                log.warn("Title evidence deletion will be retried requestId={}", cleanup.requestId());
            }
        } catch (RuntimeException exception) {
            log.error("Title evidence cleanup failed requestId={}", cleanup.requestId(), exception);
        }
        return false;
    }

    public record ImageContent(Resource resource, String mimeType) {
    }
}

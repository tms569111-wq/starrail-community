package com.starrailhearing.member.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.starrailhearing.common.web.PagedView;

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

    public long submit(long memberId, String version, MultipartFile file) {
        TitleImageStorage.StoredTitleImage image = storage.store(file);
        try {
            return persistenceService.create(memberId, version, image);
        } catch (RuntimeException exception) {
            storage.delete(image.path());
            throw exception;
        }
    }

    public List<TitleRequestView> memberViews(long memberId) {
        return persistenceService.memberViews(memberId);
    }

    public boolean hasVerifiedProfile(long memberId) {
        return persistenceService.hasVerifiedProfile(memberId);
    }

    public List<String> availableVersions(long memberId) {
        return persistenceService.availableVersions(memberId);
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
        deleteAndClear(persistenceService.cancel(memberId, requestId));
    }

    @Scheduled(initialDelayString = "1m", fixedDelayString = "1h")
    public void expirePending() {
        persistenceService.expirePending().forEach(this::deleteAndClear);
        persistenceService.pendingEvidenceCleanup().forEach(this::deleteAndClear);
    }

    private void deleteAndClear(TitleRequestPersistenceService.EvidenceCleanup cleanup) {
        try {
            if (storage.delete(cleanup.path())) {
                persistenceService.clearEvidence(cleanup.requestId(), cleanup.path());
            } else {
                log.warn("Title evidence deletion will be retried requestId={}", cleanup.requestId());
            }
        } catch (RuntimeException exception) {
            log.error("Title evidence cleanup failed requestId={}", cleanup.requestId(), exception);
        }
    }

    public record ImageContent(Resource resource, String mimeType) {
    }
}

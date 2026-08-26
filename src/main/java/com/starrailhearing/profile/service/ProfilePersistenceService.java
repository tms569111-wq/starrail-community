package com.starrailhearing.profile.service;

import com.starrailhearing.character.domain.CharacterExternalAlias;
import com.starrailhearing.character.repository.CharacterExternalAliasRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.client.PublicCharacter;
import com.starrailhearing.profile.client.PublicGameProfile;
import com.starrailhearing.profile.domain.GameProfile;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import com.starrailhearing.profile.repository.GameProfileRepository;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProfilePersistenceService {

    private final MemberService memberService;
    private final GameProfileRepository profileRepository;
    private final VerifiedCharacterRepository verifiedCharacterRepository;
    private final CharacterExternalAliasRepository aliasRepository;

    public ProfilePersistenceService(
            MemberService memberService,
            GameProfileRepository profileRepository,
            VerifiedCharacterRepository verifiedCharacterRepository,
            CharacterExternalAliasRepository aliasRepository
    ) {
        this.memberService = memberService;
        this.profileRepository = profileRepository;
        this.verifiedCharacterRepository = verifiedCharacterRepository;
        this.aliasRepository = aliasRepository;
    }

    @Transactional
    public ProfileRefreshContext reserveLookup(
            long memberId,
            String uid,
            LocalDateTime now,
            Duration cooldown
    ) {
        memberService.requireActiveForWrite(memberId);
        profileRepository.findByUid(uid)
                .filter(profile -> !profile.getMember().getId().equals(memberId))
                .ifPresent(profile -> {
                    throw new AppException(ErrorCode.UID_ALREADY_BOUND);
                });

        MemberAccount member = memberService.require(memberId);
        GameProfile profile = profileRepository.findForUpdateByMemberId(memberId)
                .map(existing -> {
                    if (existing.isVerified() && !existing.getUid().equals(uid)) {
                        throw new AppException(
                                ErrorCode.INVALID_INPUT,
                                "MVP에서는 한 계정에 하나의 UID만 연결할 수 있습니다."
                        );
                    }
                    return existing;
                })
                .orElseGet(() -> new GameProfile(member, uid));

        requireLookupAvailable(profile, now);
        profile.reserveLookup(uid, now.plus(cooldown));
        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.UID_ALREADY_BOUND, exception);
        }
        return new ProfileRefreshContext(uid);
    }

    @Transactional
    public ProfileSyncResult completeLookup(
            long memberId,
            String expectedUid,
            PublicGameProfile publicProfile,
            LocalDateTime now
    ) {
        memberService.requireActiveForWrite(memberId);
        GameProfile profile = requireProfileForUpdate(memberId);
        if (!profile.getUid().equals(expectedUid)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "조회 중 UID가 변경되었습니다. 다시 시도해 주세요.");
        }

        profile.completeVerification(
                publicProfile.provider(), publicProfile.nickname(), publicProfile.signature(), now
        );
        return syncCharacters(profile, publicProfile, now);
    }

    @Transactional
    public ProfileRefreshContext reserveRefresh(
            long memberId,
            LocalDateTime now,
            Duration cooldown
    ) {
        memberService.requireActiveForWrite(memberId);
        GameProfile profile = requireProfileForUpdate(memberId);
        requireVerified(profile);
        requireLookupAvailable(profile, now);
        profile.reserveRefresh(now.plus(cooldown));
        return new ProfileRefreshContext(profile.getUid());
    }

    @Transactional
    public ProfileSyncResult completeRefresh(
            long memberId,
            PublicGameProfile publicProfile,
            LocalDateTime now
    ) {
        memberService.requireActiveForWrite(memberId);
        GameProfile profile = requireProfileForUpdate(memberId);
        requireVerified(profile);

        ProfileSyncResult result = syncCharacters(profile, publicProfile, now);
        profile.recordSync(
                publicProfile.provider(), publicProfile.nickname(), publicProfile.signature(), now
        );
        return result;
    }

    public ProfilePageView view(long memberId, LocalDateTime now) {
        return profileRepository.findByMember_Id(memberId)
                .map(profile -> new ProfilePageView(
                        true,
                        profile.getUid(),
                        profile.getProfileNickname(),
                        profile.getVerificationStatus(),
                        profile.getChallengeCode(),
                        profile.getChallengeExpiresAt(),
                        profile.getVerifiedAt(),
                        profile.getLastSyncedAt(),
                        retryAfterSeconds(profile, now),
                        verifiedCharacterRepository
                                .findAllByProfile_IdOrderByCharacter_DisplayOrderAsc(profile.getId())
                                .stream()
                                .map(verified -> new VerifiedCharacterView(
                                        verified.getCharacter().getSlug(),
                                        verified.getCharacter().getName(),
                                        verified.getCharacter().getIconUrl(),
                                        verified.getEidolon(),
                                        verified.getLastVerifiedAt()
                                ))
                                .toList()
                ))
                .orElseGet(ProfilePageView::empty);
    }

    private ProfileSyncResult syncCharacters(
            GameProfile profile,
            PublicGameProfile publicProfile,
            LocalDateTime now
    ) {
        List<PublicCharacter> publicCharacters = publicProfile.characters();
        int added = 0;
        int upgraded = 0;
        int ignored = 0;

        List<String> externalIds = publicCharacters.stream()
                .map(PublicCharacter::externalId)
                .distinct()
                .toList();
        Map<String, CharacterExternalAlias> aliases = aliasRepository
                .findAllByProviderAndExternalIdIn(publicProfile.provider(), externalIds)
                .stream()
                .collect(Collectors.toMap(CharacterExternalAlias::getExternalId, Function.identity()));
        List<Long> characterIds = aliases.values().stream()
                .map(alias -> alias.getCharacter().getId())
                .distinct()
                .toList();
        Map<Long, VerifiedCharacter> verifiedByCharacter = characterIds.isEmpty()
                ? Map.of()
                : verifiedCharacterRepository
                        .findAllByProfile_IdAndCharacter_IdIn(profile.getId(), characterIds)
                        .stream()
                        .collect(Collectors.toMap(
                                verified -> verified.getCharacter().getId(),
                                Function.identity()
                        ));

        for (PublicCharacter observed : publicCharacters) {
            CharacterExternalAlias alias = aliases.get(observed.externalId());
            if (alias == null) {
                ignored++;
                continue;
            }

            VerifiedCharacter verified = verifiedByCharacter.get(alias.getCharacter().getId());
            if (verified == null) {
                VerifiedCharacter created = verifiedCharacterRepository.save(new VerifiedCharacter(
                        profile,
                        alias.getCharacter(),
                        observed.eidolon(),
                        now
                ));
                verifiedByCharacter.put(alias.getCharacter().getId(), created);
                added++;
                continue;
            }

            int before = verified.getEidolon();
            verified.refresh(observed.eidolon(), now);
            if (verified.getEidolon() > before) {
                upgraded++;
            }
        }

        return new ProfileSyncResult(publicCharacters.size(), added, upgraded, ignored);
    }

    private GameProfile requireProfile(long memberId) {
        return profileRepository.findByMember_Id(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private GameProfile requireProfileForUpdate(long memberId) {
        return profileRepository.findForUpdateByMemberId(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private void requireVerified(GameProfile profile) {
        if (!profile.isVerified()) {
            throw new AppException(ErrorCode.PROFILE_VERIFICATION_REQUIRED);
        }
    }

    private void requireLookupAvailable(GameProfile profile, LocalDateTime now) {
        long seconds = retryAfterSeconds(profile, now);
        if (seconds > 0) {
            throw new AppException(
                    ErrorCode.PROFILE_SYNC_COOLDOWN,
                    "너무 시도가 잦습니다. %d분 %02d초 뒤 다시 시도해 주세요."
                            .formatted(seconds / 60, seconds % 60)
            );
        }
    }

    private long retryAfterSeconds(GameProfile profile, LocalDateTime now) {
        if (profile.getNextLookupAt() == null || !now.isBefore(profile.getNextLookupAt())) {
            return 0;
        }
        long millis = Duration.between(now, profile.getNextLookupAt()).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}

record ProfileRefreshContext(String uid) {
}

package com.starrailhearing.character.service;

import com.starrailhearing.character.domain.CharacterExternalAlias;
import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.character.repository.CharacterExternalAliasRepository;
import com.starrailhearing.character.repository.GameCharacterRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.CharacterEvaluationRepository;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.service.AdminAuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CharacterAdminService {
    private final MemberService memberService;
    private final GameCharacterRepository characterRepository;
    private final CharacterExternalAliasRepository aliasRepository;
    private final GameVersionRepository versionRepository;
    private final CharacterEvaluationRepository evaluationRepository;
    private final AdminAuditService auditService;

    public CharacterAdminService(
            MemberService memberService,
            GameCharacterRepository characterRepository,
            CharacterExternalAliasRepository aliasRepository,
            GameVersionRepository versionRepository,
            CharacterEvaluationRepository evaluationRepository,
            AdminAuditService auditService
    ) {
        this.memberService = memberService;
        this.characterRepository = characterRepository;
        this.aliasRepository = aliasRepository;
        this.versionRepository = versionRepository;
        this.evaluationRepository = evaluationRepository;
        this.auditService = auditService;
    }

    public List<CharacterAdminView> views(long operatorId) {
        memberService.requireAdmin(operatorId);
        Map<Long, List<CharacterExternalAlias>> aliases = aliasRepository
                .findAllByOrderByProviderAscExternalIdAsc().stream()
                .collect(Collectors.groupingBy(alias -> alias.getCharacter().getId()));
        return characterRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(character -> toView(character, aliases.getOrDefault(character.getId(), List.of())))
                .toList();
    }

    @Transactional
    public long create(long operatorId, CharacterInput input) {
        memberService.requireAdmin(operatorId);
        requireUnique(input.canonicalExternalId(), input.slug(), null);
        try {
            GameCharacter character = input.newCharacter();
            character.hide();
            characterRepository.save(character);
            auditService.record(operatorId, null, "CHARACTER", character.getId(),
                    ModerationActionType.CREATE_CHARACTER, character.getName(), "{}", state(character));
            return character.getId();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, "이미 등록된 캐릭터 ID 또는 슬러그입니다.", exception);
        }
    }

    @Transactional
    public void update(long operatorId, long characterId, CharacterInput input) {
        memberService.requireAdmin(operatorId);
        GameCharacter character = require(characterId);
        requireUnique(input.canonicalExternalId(), input.slug(), characterId);
        String before = state(character);
        try {
            input.apply(character);
            characterRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "이미 등록된 캐릭터 ID 또는 슬러그입니다.", exception);
        }
        auditService.record(operatorId, null, "CHARACTER", characterId,
                ModerationActionType.UPDATE_CHARACTER, character.getName(), before, state(character));
    }

    @Transactional
    public void setVisible(long operatorId, long characterId, boolean visible) {
        memberService.requireAdmin(operatorId);
        GameCharacter character = require(characterId);
        String before = state(character);
        if (visible) {
            currentVersion().ifPresent(version -> {
                if (evaluationRepository.findByCharacter_IdAndVersion_Id(
                        characterId, version.getId()
                ).isEmpty()) {
                    throw new AppException(ErrorCode.VERSION_STATE_CONFLICT,
                            "진행 중인 버전에 포함되지 않은 신규 캐릭터입니다. 버전 종료 후 공개해 주세요.");
                }
            });
            character.show();
        } else {
            character.hide();
        }
        auditService.record(operatorId, null, "CHARACTER", characterId,
                visible ? ModerationActionType.SHOW_CHARACTER : ModerationActionType.HIDE_CHARACTER,
                character.getName(), before, state(character));
    }

    @Transactional
    public void upsertAlias(
            long operatorId,
            long characterId,
            ProfileProvider provider,
            String externalId
    ) {
        memberService.requireAdmin(operatorId);
        GameCharacter character = require(characterId);
        CharacterExternalAlias alias = aliasRepository
                .findByProviderAndExternalId(provider, normalizeExternalId(externalId))
                .map(existing -> {
                    existing.changeCharacter(character);
                    return existing;
                })
                .orElseGet(() -> new CharacterExternalAlias(provider, externalId, character));
        try {
            aliasRepository.saveAndFlush(alias);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "이미 등록된 공급자 외부 ID입니다.", exception);
        }
        auditService.record(operatorId, null, "CHARACTER_ALIAS", alias.getId(),
                ModerationActionType.UPDATE_EXTERNAL_ALIAS,
                provider + ":" + externalId, "{}", state(character));
    }

    private void requireUnique(String externalId, String slug, Long currentId) {
        boolean duplicateSlug = currentId == null
                ? characterRepository.existsBySlug(slug)
                : characterRepository.existsBySlugAndIdNot(slug, currentId);
        boolean duplicateExternal = currentId == null
                ? characterRepository.existsByCanonicalExternalId(externalId)
                : characterRepository.existsByCanonicalExternalIdAndIdNot(externalId, currentId);
        if (duplicateSlug || duplicateExternal) {
            throw new AppException(ErrorCode.INVALID_INPUT, "캐릭터 ID 또는 슬러그가 중복됩니다.");
        }
    }

    private GameCharacter require(long id) {
        return characterRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CHARACTER_NOT_FOUND));
    }

    private java.util.Optional<GameVersion> currentVersion() {
        return versionRepository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN)
                .or(() -> versionRepository.findFirstByStatusOrderByOpenedAtDesc(
                        VersionStatus.CLOSING
                ));
    }

    private String normalizeExternalId(String value) {
        return value == null ? "" : value.trim();
    }

    private CharacterAdminView toView(GameCharacter c, List<CharacterExternalAlias> aliases) {
        return new CharacterAdminView(
                c.getId(), c.getCanonicalExternalId(), c.getSlug(), c.getName(), c.getRarity(),
                c.getPathCode(), c.getPathName(), c.getElementCode(), c.getElementName(),
                c.getIconUrl(), c.getPortraitUrl(), c.getDisplayOrder(), c.getStatus(),
                aliases.stream().map(alias -> new CharacterAdminView.AliasView(
                        alias.getId(), alias.getProvider(), alias.getExternalId()
                )).toList()
        );
    }

    private String state(GameCharacter c) {
        return "{\"slug\":\"" + c.getSlug() + "\",\"status\":\"" + c.getStatus() + "\"}";
    }

    public record CharacterInput(
            String canonicalExternalId,
            String slug,
            String name,
            int rarity,
            String pathCode,
            String pathName,
            String elementCode,
            String elementName,
            String iconUrl,
            String portraitUrl,
            int displayOrder
    ) {
        GameCharacter newCharacter() {
            return new GameCharacter(canonicalExternalId, slug, name, rarity, pathCode, pathName,
                    elementCode, elementName, iconUrl, portraitUrl, displayOrder);
        }

        void apply(GameCharacter character) {
            character.update(canonicalExternalId, slug, name, rarity, pathCode, pathName,
                    elementCode, elementName, iconUrl, portraitUrl, displayOrder);
        }
    }
}

package com.starrailhearing.character.service;

import com.starrailhearing.character.domain.CharacterStatus;
import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.repository.GameCharacterRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CharacterService {

    private final GameCharacterRepository repository;

    public CharacterService(GameCharacterRepository repository) {
        this.repository = repository;
    }

    public List<GameCharacter> search(String keyword, String element, String path) {
        return repository.search(normalize(keyword), normalize(element), normalize(path));
    }

    public List<GameCharacter> searchByVersion(
            long versionId,
            String keyword,
            String element,
            String path
    ) {
        return repository.searchByVersion(
                versionId,
                normalize(keyword),
                normalize(element),
                normalize(path)
        );
    }

    public GameCharacter requireActive(String slug) {
        return repository.findBySlugAndStatus(slug, CharacterStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CHARACTER_NOT_FOUND));
    }

    public GameCharacter requireActiveForVersion(String slug, long versionId) {
        return repository.findActiveBySlugAndVersion(slug, versionId)
                .orElseThrow(() -> new AppException(ErrorCode.CHARACTER_NOT_FOUND));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

package com.starrailhearing.character.repository;

import com.starrailhearing.character.domain.CharacterStatus;
import com.starrailhearing.character.domain.GameCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface GameCharacterRepository extends JpaRepository<GameCharacter, Long> {

    Optional<GameCharacter> findBySlugAndStatus(String slug, CharacterStatus status);

    Optional<GameCharacter> findBySlug(String slug);

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCanonicalExternalId(String canonicalExternalId);
    boolean existsByCanonicalExternalIdAndIdNot(String canonicalExternalId, Long id);

    List<GameCharacter> findAllByStatusOrderByDisplayOrderAsc(CharacterStatus status);

    List<GameCharacter> findAllByOrderByDisplayOrderAsc();

    @Query("""
            select c from GameCharacter c
            where c.status = com.starrailhearing.character.domain.CharacterStatus.ACTIVE
              and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
              and (:element = '' or c.elementCode = :element)
              and (:path = '' or c.pathCode = :path)
            order by c.displayOrder asc, c.name asc
            """)
    List<GameCharacter> search(
            @Param("keyword") String keyword,
            @Param("element") String element,
            @Param("path") String path
    );
}

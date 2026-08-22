package com.starrailhearing.character.repository;

import com.starrailhearing.character.domain.CharacterExternalAlias;
import com.starrailhearing.character.domain.ProfileProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface CharacterExternalAliasRepository extends JpaRepository<CharacterExternalAlias, Long> {

    @EntityGraph(attributePaths = "character")
    Optional<CharacterExternalAlias> findByProviderAndExternalId(
            ProfileProvider provider,
            String externalId
    );

    @EntityGraph(attributePaths = "character")
    List<CharacterExternalAlias> findAllByProviderAndExternalIdIn(
            ProfileProvider provider,
            Collection<String> externalIds
    );

    @EntityGraph(attributePaths = "character")
    List<CharacterExternalAlias> findAllByOrderByProviderAscExternalIdAsc();
}

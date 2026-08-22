package com.starrailhearing.profile.repository;

import com.starrailhearing.profile.domain.VerifiedCharacter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface VerifiedCharacterRepository extends JpaRepository<VerifiedCharacter, Long> {

    Optional<VerifiedCharacter> findByProfile_IdAndCharacter_Id(Long profileId, Long characterId);

    @EntityGraph(attributePaths = "character")
    List<VerifiedCharacter> findAllByProfile_IdAndCharacter_IdIn(
            Long profileId,
            Collection<Long> characterIds
    );

    @EntityGraph(attributePaths = {"profile", "profile.member", "character"})
    Optional<VerifiedCharacter> findByProfile_Member_IdAndCharacter_Id(Long memberId, Long characterId);

    @EntityGraph(attributePaths = "character")
    List<VerifiedCharacter> findAllByProfile_IdOrderByCharacter_DisplayOrderAsc(Long profileId);

    void deleteAllByProfile_Id(Long profileId);
}

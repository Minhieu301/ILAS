package com.C1SE10.backend.repository;

import com.C1SE10.backend.model.LawVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawVersionRepository extends JpaRepository<LawVersion, Integer> {

    List<LawVersion> findByLawIdOrderByVersionNumberDesc(Integer lawId);

    Optional<LawVersion> findByLawIdAndVersionNumber(Integer lawId, Integer versionNumber);

    @Query("SELECT v FROM LawVersion v WHERE v.lawId = :lawId ORDER BY v.versionNumber DESC")
    List<LawVersion> findAllByLawIdOrderByVersionNumberDesc(@Param("lawId") Integer lawId);
}
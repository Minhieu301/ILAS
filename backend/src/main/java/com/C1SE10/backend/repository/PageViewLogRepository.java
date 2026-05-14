package com.C1SE10.backend.repository;

import com.C1SE10.backend.model.PageViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PageViewLogRepository extends JpaRepository<PageViewLog, Integer> {

    List<PageViewLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}


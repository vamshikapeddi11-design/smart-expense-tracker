package com.expensetracker.repository;

import com.expensetracker.model.SyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {
    List<SyncRun> findByUserIdOrderByStartedAtDesc(Long userId);
}

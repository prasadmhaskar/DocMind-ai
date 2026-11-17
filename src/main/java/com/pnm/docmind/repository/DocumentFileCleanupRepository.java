package com.pnm.docmind.repository;

import com.pnm.docmind.entity.DocumentFileCleanupTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentFileCleanupRepository extends JpaRepository<DocumentFileCleanupTask, String> {
    List<DocumentFileCleanupTask> findTop100ByOrderByCreatedAtAsc();
}

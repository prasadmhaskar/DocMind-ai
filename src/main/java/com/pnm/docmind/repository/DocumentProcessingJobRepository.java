package com.pnm.docmind.repository;

import com.pnm.docmind.entity.DocumentProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, Long> {
}

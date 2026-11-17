package com.pnm.docmind.repository;

import com.pnm.docmind.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByTokenHash(String tokenHash);
}

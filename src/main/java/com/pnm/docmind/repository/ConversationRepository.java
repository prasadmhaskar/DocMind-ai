package com.pnm.docmind.repository;

import com.pnm.docmind.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByIdAndDocument_IdAndWorkspace_Id(Long id, Long documentId, UUID workspaceId);

    List<Conversation> findByDocument_IdAndWorkspace_IdOrderByUpdatedAtDescIdDesc(
            Long documentId,
            UUID workspaceId);

    @Modifying
    @Query("update Conversation c set c.updatedAt = CURRENT_TIMESTAMP where c.id = :conversationId")
    void updateLastMessageTime(@Param("conversationId") Long conversationId);
}

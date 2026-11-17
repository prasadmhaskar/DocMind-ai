package com.pnm.docmind.repository;

import com.pnm.docmind.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversation_IdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<ChatMessage> findByConversation_IdOrderByIdAsc(Long conversationId);
}

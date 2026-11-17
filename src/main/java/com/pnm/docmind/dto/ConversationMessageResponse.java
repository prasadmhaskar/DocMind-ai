package com.pnm.docmind.dto;

import com.pnm.docmind.constant.ChatMessageRole;

import java.time.LocalDateTime;

public record ConversationMessageResponse(
        Long messageId,
        ChatMessageRole role,
        String content,
        LocalDateTime createdAt
) {
}

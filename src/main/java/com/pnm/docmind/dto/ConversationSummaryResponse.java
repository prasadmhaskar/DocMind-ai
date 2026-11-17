package com.pnm.docmind.dto;

import java.time.LocalDateTime;

public record ConversationSummaryResponse(
        Long conversationId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

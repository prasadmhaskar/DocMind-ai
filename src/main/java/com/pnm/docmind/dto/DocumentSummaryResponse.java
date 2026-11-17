package com.pnm.docmind.dto;

import com.pnm.docmind.constant.DocumentStatus;

import java.time.LocalDateTime;

public record DocumentSummaryResponse(
        Long documentId,
        String originalFilename,
        String contentType,
        Long fileSize,
        DocumentStatus documentStatus,
        LocalDateTime uploadedAt,
        boolean publicDocument
) {
}

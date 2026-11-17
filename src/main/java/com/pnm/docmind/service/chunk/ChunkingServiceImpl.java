package com.pnm.docmind.service.chunk;

import com.pnm.docmind.dto.DocumentChunkData;
import com.pnm.docmind.dto.PageContent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkingServiceImpl implements ChunkingService {

    @Value("${chunk.max.size}")
    private int maxChunkSize;

    @Value("${chunk.overlap.size}")
    private int overlapSize;

    @Override
    public List<DocumentChunkData> chunk(PageContent page, int chunkIndex) {

        Objects.requireNonNull(page);

        log.info("Started chunking page={} length={}", page.pageNumber(), page.text().length());

        List<DocumentChunkData> chunks = new ArrayList<>();

        int textLength = page.text().length();
        String currentPageText = page.text();

        if (currentPageText.isBlank()) {
            return List.of();
        }

        if (maxChunkSize >= textLength) {
            return List.of(new DocumentChunkData(page.pageNumber(), chunkIndex, currentPageText.trim()));
        }

        int start = 0;

        while (start < textLength) {

            int proposedEnd = Math.min(start + maxChunkSize, textLength);
            int end = proposedEnd < textLength
                    ? findPreferredBoundary(currentPageText, start, proposedEnd)
                    : proposedEnd;

            String chunk = currentPageText.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(new DocumentChunkData(page.pageNumber(), chunkIndex, chunk));
            }

            if (end >= textLength) {
                break;
            }

            int targetNextStart = Math.max(start + 1, end - overlapSize);
            int nextStart = alignToNextWord(currentPageText, targetNextStart, end);

            if (nextStart <= start || nextStart >= end) {
                nextStart = targetNextStart;
            }

            start = nextStart;

            chunkIndex++;
        }

        log.info(
                "Chunking completed. currentPage={}, maxChunkSize={}, overlapSize={}, totalChunks={}",
                page.pageNumber(),
                maxChunkSize,
                overlapSize,
                chunks.size()
        );

        return chunks;

    }

    private int findPreferredBoundary(String text, int start, int limit) {
        int earliestBoundary = Math.min(limit, start + Math.max(1, maxChunkSize / 2));

        int boundary = findLastBoundary(text, earliestBoundary, limit, BoundaryType.PARAGRAPH);
        if (boundary > start) {
            return boundary;
        }

        boundary = findLastBoundary(text, earliestBoundary, limit, BoundaryType.SENTENCE);
        if (boundary > start) {
            return boundary;
        }

        boundary = findLastBoundary(text, earliestBoundary, limit, BoundaryType.WORD);
        return boundary > start ? boundary : limit;
    }

    private int findLastBoundary(String text, int earliestBoundary, int limit, BoundaryType type) {
        for (int index = limit - 1; index >= earliestBoundary; index--) {
            char character = text.charAt(index);

            if (type == BoundaryType.PARAGRAPH && isParagraphBreak(text, index)) {
                return index + 1;
            }

            if (type == BoundaryType.SENTENCE
                    && isSentenceEnd(character)
                    && (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)))) {
                return index + 1;
            }

            if (type == BoundaryType.WORD && Character.isWhitespace(character)) {
                return index + 1;
            }
        }
        return -1;
    }

    private boolean isParagraphBreak(String text, int index) {
        if (text.charAt(index) != '\n') {
            return false;
        }

        int previousCharacter = index - 1;
        if (previousCharacter >= 0 && text.charAt(previousCharacter) == '\r') {
            previousCharacter--;
        }
        return previousCharacter >= 0 && text.charAt(previousCharacter) == '\n';
    }

    private boolean isSentenceEnd(char character) {
        return character == '.' || character == '?' || character == '!';
    }

    private int alignToNextWord(String text, int start, int limit) {
        int alignedStart = start;
        while (alignedStart < limit && !Character.isWhitespace(text.charAt(alignedStart))) {
            alignedStart++;
        }
        while (alignedStart < limit && Character.isWhitespace(text.charAt(alignedStart))) {
            alignedStart++;
        }
        return alignedStart;
    }

    private enum BoundaryType {
        PARAGRAPH,
        SENTENCE,
        WORD
    }


    @PostConstruct
    public void validateChunkSizeEnvVariables() {

        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunk.max.size must be greater than 0"
            );
        }

        if (overlapSize < 0 || overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException(
                    "chunk.overlap.size must be >= 0 and < chunk.max.size"
            );
        }
    }
}

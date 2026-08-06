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

        if (maxChunkSize >= textLength) {
            return List.of(new DocumentChunkData(page.pageNumber(), chunkIndex, currentPageText));
        }

        int start = 0;

        while (start < textLength) {

            int proposedEnd = Math.min(start + maxChunkSize, textLength);

            int end = proposedEnd;

            if (proposedEnd < textLength) {

                while (end > start && isNotBoundary(currentPageText.charAt(end - 1))) {
                    end--;
                }

                if (end == start) {
                    end = proposedEnd;
                }
            }

            String chunk = currentPageText.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(new DocumentChunkData(page.pageNumber(), chunkIndex, chunk));
            }

            if (end >= textLength) {
                break;
            }

            int nextStart = Math.max(end - overlapSize, 0);

            if (nextStart <= start) {nextStart = end;}

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

    private boolean isNotBoundary(char character) {
        return
                character != '\n' &&
                character != '.' &&
                character != '?' &&
                character != '!' &&
                character != ':' &&
                character != ';' &&
                character != ')' &&
                !Character.isWhitespace(character);
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
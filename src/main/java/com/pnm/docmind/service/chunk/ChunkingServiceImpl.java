package com.pnm.docmind.service.chunk;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkingServiceImpl implements ChunkingService {

    @Value("${chunk.max.size}")
    private int maxChunkSize;

    @Value("${chunk.overlap.size}")
    private int overlapSize;

    @Override
    public List<String> chunk(String extractedText) {

        log.info("Started chunking");

        if (extractedText == null || extractedText.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();

        int textLength = extractedText.length();

        if (maxChunkSize >= textLength) {
            return List.of(extractedText.trim());
        }

        int start = 0;

        while (start < textLength) {

            int proposedEnd = Math.min(start + maxChunkSize, textLength);

            int end = proposedEnd;

            if (proposedEnd < textLength) {

                while (end > start && !isBoundary(extractedText.charAt(end - 1))) {
                    end--;
                }

                if (end == start) {
                    end = proposedEnd;
                }
            }

            String chunk = extractedText.substring(start, end).trim();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= textLength) {
                break;
            }

            int nextStart = Math.max(end - overlapSize, 0);

            if (nextStart <= start) {nextStart = end;}

            start = nextStart;
        }

        log.info(
                "Chunking completed. inputLength={}, maxChunkSize={}, overlapSize={}, totalChunks={}",
                textLength,
                maxChunkSize,
                overlapSize,
                chunks.size()
        );

        return chunks;
    }


    private boolean isBoundary(char character) {
        return
                character == '\n' ||
                character == '.' ||
                Character.isWhitespace(character);
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
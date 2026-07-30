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

        List<String> chunkedTextList = new ArrayList<>();

        int start = 0;

        if(maxChunkSize >= extractedText.length()) {
            return List.of(extractedText);
        }

        while (start < extractedText.length()) {

            int proposedEnd = Math.min(start + maxChunkSize, extractedText.length());
            int end = proposedEnd;

            while(end > start &&
                    extractedText.charAt(end) != '\n' &&
                    extractedText.charAt(end) != '.' &&
                    extractedText.charAt(end) != ' ')
            {
                end--;
            }

            if(end == start){
                end = proposedEnd;
            }

            String chunk = extractedText.substring(start, end);

            if (!chunk.isBlank()) {
                chunkedTextList.add(chunk);
            }

            start = Math.max(end - overlapSize, 0);
        }

        log.info("Input text length:{}, max chunk size:{}, overlap size:{}, total generated chunks:{}", extractedText.length(), maxChunkSize, overlapSize,  chunkedTextList.size());

        return chunkedTextList;
    }


    @PostConstruct
    public void validateChunkSizeEnvVariables(){

        if(maxChunkSize <= 0) {
            throw new IllegalArgumentException("Invalid ENV variable value set for {chunk.max.size}");
        }

        if(overlapSize <= 0 || overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException("Invalid ENV variable value set for {chunk.overlap.size}");
        }
    }
}

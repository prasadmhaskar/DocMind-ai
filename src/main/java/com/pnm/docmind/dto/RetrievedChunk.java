package com.pnm.docmind.dto;

public record RetrievedChunk(Integer pageNumber, Integer chunkIndex, String content, Double similarity) {}

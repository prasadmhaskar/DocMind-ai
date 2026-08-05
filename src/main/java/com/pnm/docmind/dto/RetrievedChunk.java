package com.pnm.docmind.dto;

public record RetrievedChunk(Integer chunkIndex, String content, Double similarity) {}

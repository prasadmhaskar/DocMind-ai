package com.pnm.docmind.dto;

public record RetrievedChunkResponse(Integer chunkIndex,
                                     Double similarity,
                                     String content
) {}

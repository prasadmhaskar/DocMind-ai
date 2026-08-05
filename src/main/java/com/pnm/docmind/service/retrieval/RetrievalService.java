package com.pnm.docmind.service.retrieval;

import com.pnm.docmind.dto.RetrievedChunkResponse;

import java.util.List;

public interface RetrievalService {

    List<RetrievedChunkResponse> retrieve(Long documentId, String question);
}

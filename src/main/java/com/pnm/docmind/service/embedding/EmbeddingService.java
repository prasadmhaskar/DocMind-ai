package com.pnm.docmind.service.embedding;

import java.util.List;

public interface EmbeddingService {

    List<float[]> generateDocumentEmbedding(List<String> chunkedTextList);
    float[] generateQueryEmbedding(String queryText);
}

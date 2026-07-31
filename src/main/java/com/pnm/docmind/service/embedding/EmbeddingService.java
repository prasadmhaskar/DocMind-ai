package com.pnm.docmind.service.embedding;

import java.util.List;

public interface EmbeddingService {

    List<float[]> generateEmbedding(List<String> chunkedTextList);
}

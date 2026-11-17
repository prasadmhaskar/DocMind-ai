package com.pnm.docmind.service.embedding;

import com.pnm.docmind.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int MAX_EMBEDDING_BATCH_SIZE = 100;

    private final EmbeddingModel embeddingModel;


    @Override
    public List<float[]> generateDocumentEmbedding(List<String> chunkedTextList) {

        if (chunkedTextList.isEmpty()) {
            throw new IllegalArgumentException(
                    "Text for embedding cannot be empty"
            );
        }

        try{
            List<float[]> embeddings = new ArrayList<>(chunkedTextList.size());
            for (int start = 0; start < chunkedTextList.size(); start += MAX_EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, chunkedTextList.size());
                List<String> batch = chunkedTextList.subList(start, end);
                List<float[]> batchEmbeddings = embeddingModel.embed(batch);
                if (batchEmbeddings.size() != batch.size()) {
                    throw new DocumentProcessingException("Embedding model returned a different number of embeddings than requested");
                }
                embeddings.addAll(batchEmbeddings);
                log.info("Generated embeddings for chunks {}-{} of {}", start + 1, end, chunkedTextList.size());
            }

            if(embeddings.isEmpty())
                throw new DocumentProcessingException("Embedding model returned an empty embedding");

            log.info("Embedding model returned {} embeddings", embeddings.size());

            return embeddings;
        }
        catch (Exception e){
            log.error("Failed to generate embedding", e);

            throw new DocumentProcessingException("Failed to generate embedding: " + e);
        }

    }


    @Override
    public float[] generateQueryEmbedding(String queryText) {

        if (queryText.isEmpty()) {
            throw new IllegalArgumentException("Text for embedding cannot be empty");
        }

        try{
            float[] embeddings = embeddingModel.embed(queryText);

            if(embeddings.length == 0)
                throw new DocumentProcessingException("Embedding model returned an empty embedding");

            log.info("Embedding model returned {} embeddings", embeddings.length);

            return embeddings;
        }
        catch (Exception e){
            log.error("Failed to generate embedding", e);

            throw new DocumentProcessingException("Failed to generate embedding: " + e);
        }

    }
}

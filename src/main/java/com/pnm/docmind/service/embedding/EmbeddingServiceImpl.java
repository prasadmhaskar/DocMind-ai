package com.pnm.docmind.service.embedding;

import com.pnm.docmind.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;


    @Override
    public List<float[]> generateEmbedding(List<String> chunkedTextList) {

        if (chunkedTextList.isEmpty()) {
            throw new IllegalArgumentException(
                    "Text for embedding cannot be empty"
            );
        }

        try{
            List<float[]> embeddings = embeddingModel.embed(chunkedTextList);

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
}

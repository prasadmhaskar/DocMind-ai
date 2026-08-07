package com.pnm.docmind.service.document;

import com.pnm.docmind.constant.DocumentStatus;
import com.pnm.docmind.dto.DocumentChunkData;
import com.pnm.docmind.entity.Document;
import com.pnm.docmind.entity.DocumentChunk;
import com.pnm.docmind.exception.DocumentProcessingException;
import com.pnm.docmind.repository.DocumentChunkRepository;
import com.pnm.docmind.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistDocumentChunk {

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;


    @Transactional
    public void persist(Document document, List<DocumentChunkData> documentChunks, List<float[]> embeddings) {

        if (documentChunks.size() != embeddings.size()) {
            throw new DocumentProcessingException("Chunk and embedding count mismatch");
        }

        List<DocumentChunk> documentChunkList = new ArrayList<>();

        for(int i=0; i<documentChunks.size(); i++) {

            DocumentChunk documentChunk = DocumentChunk.builder()
                    .document(document)
                    .chunkIndex(documentChunks.get(i).chunkIndex())
                    .content(documentChunks.get(i).content())
                    .pageNumber(documentChunks.get(i).pageNumber())
                    .embedding(embeddings.get(i))
                    .build();

            documentChunkList.add(documentChunk);
        }

        documentChunkRepository.saveAll(documentChunkList);

        document.setDocumentStatus(DocumentStatus.READY);

        documentRepository.save(document);

        log.info("Persisted {} chunks for documentId={}", documentChunkList.size(), document.getId());

    }

}

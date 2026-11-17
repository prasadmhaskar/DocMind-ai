package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.dto.DocumentSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    UploadDocumentResponse upload(MultipartFile file, UUID workspaceId);
    UploadDocumentResponse getDocumentById(Long documentId, UUID workspaceId);
    List<DocumentSummaryResponse> listDocuments(UUID workspaceId);
    void deleteDocument(Long documentId, UUID workspaceId);
    void deleteExpiredDocument(Long documentId);
}

package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.UploadDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    UploadDocumentResponse upload(MultipartFile file);
    UploadDocumentResponse getDocumentById(String documentId);
}

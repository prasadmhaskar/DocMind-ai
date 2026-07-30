package com.pnm.docmind.controller;

import com.pnm.docmind.dto.UploadDocumentResponse;
import com.pnm.docmind.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<UploadDocumentResponse> upload(@RequestParam("file") MultipartFile file) {

        UploadDocumentResponse response = documentService.upload(file);

        return ResponseEntity.accepted().body(response);

    }

    @GetMapping("/{id}")
    public ResponseEntity<UploadDocumentResponse> getDocumentStatus(@PathVariable String id) {

        UploadDocumentResponse documentResponse = documentService.getDocumentById(id);

        return ResponseEntity.ok(documentResponse);
    }
}

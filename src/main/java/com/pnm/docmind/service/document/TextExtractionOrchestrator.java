package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;
import com.pnm.docmind.exception.PdfProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TextExtractionOrchestrator {

    private final PdfExtractionService pdfExtractionService;
    private final OcrExtractionService ocrExtractionService;

    public List<PageContent> extract(Path path) {

        if(Files.notExists(path)) {
            throw new PdfProcessingException("PDF file not found: " +path);
        }
        if(Files.isDirectory(path)) {
            throw new PdfProcessingException("PDF file is a directory: " +path);
        }

        List<PageContent> extractPdfPageContent = pdfExtractionService.extractText(path);

//        int totalExtractedCharacters = extractPdfPageContent.stream()
//                .map(PageContent::text)
//                .mapToInt(String::length)
//                .sum();

        String text = extractPdfPageContent.stream()
                .map(PageContent::text)
                .collect(Collectors.joining());

        if(text.strip().length() < 50){
            return ocrExtractionService.extract(path);
        }

        return extractPdfPageContent;

    }
}

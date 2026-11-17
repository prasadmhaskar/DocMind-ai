package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;
import com.pnm.docmind.exception.PdfProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TextExtractionOrchestrator {

    private static final int MIN_NATIVE_PAGE_CHARACTERS = 40;

    private final PdfExtractionService pdfExtractionService;
    private final OcrExtractionService ocrExtractionService;

    public List<PageContent> extract(Path path) {
        if (Files.notExists(path)) {
            throw new PdfProcessingException("PDF file not found: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new PdfProcessingException("PDF file is a directory: " + path);
        }

        List<PageContent> nativePages = pdfExtractionService.extractText(path);
        List<Integer> pagesNeedingOcr = nativePages.stream()
                .filter(page -> page.text().strip().length() < MIN_NATIVE_PAGE_CHARACTERS)
                .map(PageContent::pageNumber)
                .toList();

        if (pagesNeedingOcr.isEmpty()) {
            return nativePages;
        }

        Map<Integer, String> ocrTextByPage = new HashMap<>();
        for (PageContent page : ocrExtractionService.extractPages(path, pagesNeedingOcr)) {
            ocrTextByPage.put(page.pageNumber(), page.text());
        }

        List<PageContent> combinedPages = new ArrayList<>(nativePages.size());
        for (PageContent nativePage : nativePages) {
            combinedPages.add(new PageContent(
                    nativePage.pageNumber(),
                    combinePageText(nativePage.text(), ocrTextByPage.get(nativePage.pageNumber()))));
        }
        return combinedPages;
    }

    private String combinePageText(String nativeText, String ocrText) {
        String nativeContent = nativeText == null ? "" : nativeText.strip();
        String ocrContent = ocrText == null ? "" : ocrText.strip();

        if (nativeContent.isEmpty()) {
            return ocrContent;
        }
        if (ocrContent.isEmpty()) {
            return nativeContent;
        }

        String normalizedNative = normalizeForComparison(nativeContent);
        String normalizedOcr = normalizeForComparison(ocrContent);
        if (normalizedNative.equals(normalizedOcr)) {
            return nativeContent;
        }
        return nativeContent + "\n" + ocrContent;
    }

    private String normalizeForComparison(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}

package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;
import com.pnm.docmind.exception.PdfProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@Slf4j
public class OcrExtractionServiceImpl implements OcrExtractionService {

    private static final long OCR_TIMEOUT_MINUTES = 2;

    private final String executable;
    private final String language;
    private final int dpi;

    public OcrExtractionServiceImpl(
            @Value("${ocr.executable:tesseract}") String executable,
            @Value("${ocr.language:eng}") String language,
            @Value("${ocr.dpi:300}") int dpi
    ) {
        this.executable = executable;
        this.language = language;
        this.dpi = dpi;
    }

    @Override
    public List<PageContent> extract(Path pdfPath) {
        validatePdfPath(pdfPath);

        long start = System.nanoTime();
        Path temporaryDirectory = null;

        try {
            temporaryDirectory = Files.createTempDirectory("docmind-ocr-");

            try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
                if (document.isEncrypted()) {
                    throw new PdfProcessingException("PDF file is encrypted: " + pdfPath);
                }

                log.info("Starting OCR extraction. pages={}, language={}, dpi={}",
                        document.getNumberOfPages(), language, dpi);

                PDFRenderer renderer = new PDFRenderer(document);
                List<PageContent> pages = new ArrayList<>();

                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    String text = extractPage(renderer, pageIndex, temporaryDirectory);

                    if (!text.isBlank()) {
                        pages.add(new PageContent(pageIndex + 1, text.trim()));
                    }
                }

                log.info("OCR completed. extractedPages={}, elapsedMs={}",
                        pages.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

                return pages;
            }
        } catch (IOException e) {
            throw new PdfProcessingException("Failed to extract text with OCR: " + e.getMessage());
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private String extractPage(PDFRenderer renderer, int pageIndex, Path temporaryDirectory) throws IOException {
        Path imagePath = temporaryDirectory.resolve("page-" + (pageIndex + 1) + ".png");
        Path outputBasePath = temporaryDirectory.resolve("page-" + (pageIndex + 1));
        Path processLogPath = temporaryDirectory.resolve("page-" + (pageIndex + 1) + ".log");
        Path outputTextPath = temporaryDirectory.resolve("page-" + (pageIndex + 1) + ".txt");

        BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
        if (!ImageIO.write(image, "png", imagePath.toFile())) {
            throw new IOException("No PNG writer is available");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                executable,
                imagePath.toString(),
                outputBasePath.toString(),
                "-l",
                language
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(processLogPath.toFile());

        try {
            Process process = processBuilder.start();

            if (!process.waitFor(OCR_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new PdfProcessingException("OCR timed out for page " + (pageIndex + 1));
            }

            if (process.exitValue() != 0) {
                String processOutput = Files.readString(processLogPath, StandardCharsets.UTF_8).trim();
                throw new PdfProcessingException("OCR failed for page " + (pageIndex + 1)
                        + " with exit code " + process.exitValue() + ": " + processOutput);
            }

            return Files.readString(outputTextPath, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfProcessingException("OCR interrupted for page " + (pageIndex + 1));
        }
    }

    private void validatePdfPath(Path pdfPath) {
        if (Files.notExists(pdfPath)) {
            throw new PdfProcessingException("PDF file not found: " + pdfPath);
        }

        if (Files.isDirectory(pdfPath)) {
            throw new PdfProcessingException("PDF file is a directory: " + pdfPath);
        }
    }

    private void deleteTemporaryDirectory(Path temporaryDirectory) {
        if (temporaryDirectory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete OCR temporary file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean OCR temporary directory: {}", temporaryDirectory, e);
        }
    }
}

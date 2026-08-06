package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;
import com.pnm.docmind.exception.PdfProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class PdfExtractionServiceImpl implements PdfExtractionService {


    @Override
    public List<PageContent> extractText(Path pdfPath) {

        List<PageContent> pages = new ArrayList<>();

        if(Files.notExists(pdfPath)) {
            throw new PdfProcessingException("PDF file not found: " + pdfPath);
        }
        if(Files.isDirectory(pdfPath)) {
            throw new PdfProcessingException("PDF file is a directory: " + pdfPath);
        }

        try(PDDocument document = Loader.loadPDF(pdfPath.toFile())) {

            if(document.isEncrypted()) {
                throw new PdfProcessingException("PDF file is encrypted: " + pdfPath);
            }

            log.info("PDF has {} pages", document.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();

            for(int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);


                String pageText = stripper.getText(document);

                if(pageText.isBlank()){
                    continue;
                }

                pages.add(new PageContent(pageNumber, pageText));
            }

            return pages;

        } catch (IOException e) {
            throw new PdfProcessingException("Failed to extract text: " + e);
        }

    }
}

package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;

import java.nio.file.Path;
import java.util.List;

public interface PdfExtractionService {

    List<PageContent> extractText(Path pdfPath);
}

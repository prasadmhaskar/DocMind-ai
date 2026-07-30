package com.pnm.docmind.service.document;

import java.nio.file.Path;

public interface PdfExtractionService {

    String extractText(Path pdfPath);
}

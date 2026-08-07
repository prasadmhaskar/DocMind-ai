package com.pnm.docmind.service.document;

import com.pnm.docmind.dto.PageContent;
import java.nio.file.Path;
import java.util.List;

public interface OcrExtractionService {

    List<PageContent> extract(Path pdfPath);
}

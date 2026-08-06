package com.pnm.docmind.service.chunk;

import com.pnm.docmind.dto.DocumentChunkData;
import com.pnm.docmind.dto.PageContent;

import java.util.List;

public interface ChunkingService {

    List<DocumentChunkData> chunk(PageContent page, int chunkIndex);
}

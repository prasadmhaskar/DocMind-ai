package com.pnm.docmind.service.chunk;

import java.util.List;

public interface ChunkingService {

    List<String> chunk(String extractedText);
}

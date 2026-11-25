package com.pnm.docmind.dto;

import org.springframework.core.io.Resource;

public record DocumentFileDownload(Resource resource, String filename) {
}

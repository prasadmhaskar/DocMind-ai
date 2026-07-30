package com.pnm.docmind.dto;

import com.pnm.docmind.constant.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class UploadDocumentResponse {

    public Long documentId;
    public DocumentStatus documentStatus;

}

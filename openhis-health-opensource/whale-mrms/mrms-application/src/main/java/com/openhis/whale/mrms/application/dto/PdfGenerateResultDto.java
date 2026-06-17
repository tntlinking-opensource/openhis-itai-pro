package com.openhis.whale.mrms.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PdfGenerateResultDto {

    private String pdfBase64;
    private Integer pageCount;
    private String contentHash;
    private Instant renderedAt;
}

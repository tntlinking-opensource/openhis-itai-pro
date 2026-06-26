/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地 PDF 生成结果。 */
@Data
@Accessors(chain = true)
public class ElepPdfGenerateResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
    private String pdfFilePath;
}

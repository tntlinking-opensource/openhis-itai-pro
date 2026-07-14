/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

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

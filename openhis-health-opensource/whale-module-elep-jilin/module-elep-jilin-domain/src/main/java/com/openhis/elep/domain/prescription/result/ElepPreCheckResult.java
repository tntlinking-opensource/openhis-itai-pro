/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** A0001 预核验结果。 */
@Data
@Accessors(chain = true)
public class ElepPreCheckResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
    private String hospRxno;
    private String rxTraceCode;
    private String hiRxno;
    private String pdfFilePath;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** A0002 医保电子签名结果。 */
@Data
@Accessors(chain = true)
public class ElepSignResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
    private String signDigest;
    private String pdfFilePath;
}

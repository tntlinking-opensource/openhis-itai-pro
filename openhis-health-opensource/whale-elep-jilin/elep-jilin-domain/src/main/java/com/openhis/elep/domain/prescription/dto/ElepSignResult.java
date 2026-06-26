/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

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

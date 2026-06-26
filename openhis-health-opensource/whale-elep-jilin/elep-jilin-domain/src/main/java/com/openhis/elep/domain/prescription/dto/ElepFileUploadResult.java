/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** A0003 上传结果。 */
@Data
@Accessors(chain = true)
public class ElepFileUploadResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
    private String hiRxno;
    private String rxStasCodg;
    private String rxStasName;
}

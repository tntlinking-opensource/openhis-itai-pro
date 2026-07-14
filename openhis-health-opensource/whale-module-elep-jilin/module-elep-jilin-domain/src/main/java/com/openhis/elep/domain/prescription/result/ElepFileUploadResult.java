/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

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

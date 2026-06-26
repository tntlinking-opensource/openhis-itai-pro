/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** A0004 撤销结果（Bridge API）。 */
@Data
@Accessors(chain = true)
public class ElepRevokeResult {

    private boolean success;
    private String message;
    private Long prescriptionId;
}

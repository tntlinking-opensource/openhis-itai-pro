/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 套用/续方草稿摘要。 */
@Data
@Accessors(chain = true)
public class ElepPrescriptionDraftDto {

    private Long prescriptionId;
    private String hospRxno;
    private String initRxno;
    private String localStatus;
}

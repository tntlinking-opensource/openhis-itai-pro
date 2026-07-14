/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 套用/续方生成的草稿摘要。 */
@Data
@Accessors(chain = true)
public class ElepPrescriptionDraft {

    private Long prescriptionId;
    private String hospRxno;
    private String initRxno;
    private String localStatus;
}

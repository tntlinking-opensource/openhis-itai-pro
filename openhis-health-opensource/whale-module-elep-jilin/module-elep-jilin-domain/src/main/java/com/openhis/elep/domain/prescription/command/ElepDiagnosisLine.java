/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方诊断行（医保 1307，不写 {@code adm_encounter_diagnosis}）。
 */
@Data
@Accessors(chain = true)
public class ElepDiagnosisLine {

    /** 医保诊断编码。 */
    private String diagCode;

    /** 医保诊断名称。 */
    private String diagName;
}

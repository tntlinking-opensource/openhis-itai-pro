/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 皮试药草稿行中性规格（门诊/住院医生站皮试联动共用）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class SkinTestMedicationDraftSpec {

    /** 是否皮试药。 */
    private Boolean skinTestFlag;

    /** 费用性质/合同。 */
    private Long contractId;

    /** 诊断目录 id。 */
    private Long conditionDefinitionId;

    /** 就诊诊断关联 id。 */
    private Long encounterDiagnosisId;
}

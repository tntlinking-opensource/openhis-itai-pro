/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊已签发处方筛选窗内去重汇总（AS-OP-PHA-005 页脚指标）。
 *
 * <p>与 {@code pageOutpatientSignedPrescriptionDrugLines} 共用同一规约；禁止拉全量明细再内存去重。</p>
 */
@Getter
@Setter
public class OutpatientSignedPrescriptionSummaryCountQueryModel {

    /** 总处方量：{@code prescription_no} 去重。 */
    private Integer prescriptionCount = 0;

    /** 就诊人数：{@code encounter_id} 去重。 */
    private Integer visitPatientCount = 0;
}

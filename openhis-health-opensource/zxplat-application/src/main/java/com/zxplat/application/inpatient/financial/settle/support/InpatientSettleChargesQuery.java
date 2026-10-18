/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院结算费用读查询（IF-IP-STL-04）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleChargesQuery {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键；须与就诊归属一致（Gate）。 */
    private Long patientId;

    /** 费用呈现模式：{@code detail} 明细行 / {@code summary} 大类汇总。 */
    private String presentationMode;

    /** 费用业务日下限；空时回落入院日。 */
    private LocalDate chargeDateFrom;

    /** 费用业务日上限；空时回落当日。 */
    private LocalDate chargeDateTo;
}

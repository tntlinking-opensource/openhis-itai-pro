/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 追溯码分配汇总组内底层发放行数量快照（**AS-IP-NUR-015 · IF-IP-TRC-02**）。
 *
 * <p>拆零累加由应用层按药品目录 unitBands 完成。</p>
 */
@Getter
@Setter
public class PatientTraceAssignmentDispenseLineQueryModel {

    /** 发放行主键。 */
    private Long dispenseId;

    /** 发放数量（原单位）。 */
    private BigDecimal quantity;

    /** 发放单位编码。 */
    private String unitCode;

    /** 已分配追溯码（可空）。 */
    private String traceNo;
}

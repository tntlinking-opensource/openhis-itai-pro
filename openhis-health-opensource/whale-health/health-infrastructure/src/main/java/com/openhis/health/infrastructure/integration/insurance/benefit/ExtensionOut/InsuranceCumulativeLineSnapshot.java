/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 人员累计信息行快照（5206 等累计查询单行）。
 */
@Data
@Accessors(chain = true)
public class InsuranceCumulativeLineSnapshot {

    /** 险种类型 code。 */
    private String insutype;

    /** 年度。 */
    private String year;

    /** 累计年月 YYYYMM。 */
    private String cumYm;

    /** 累计类别 code。 */
    private String cumTypeCode;

    /** 累计金额/次数。 */
    private BigDecimal cum;
}

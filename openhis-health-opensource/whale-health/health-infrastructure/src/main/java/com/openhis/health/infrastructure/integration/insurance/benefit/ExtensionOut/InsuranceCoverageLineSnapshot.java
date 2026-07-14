/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 参保险种行快照（1101 险种列表单行）。
 */
@Data
@Accessors(chain = true)
public class InsuranceCoverageLineSnapshot {

    /** 参保地医保区划 code。 */
    private String insuplcAdmdvs;

    /** 险种类型 code。 */
    private String insutype;

    /** 人员类别 code。 */
    private String psnType;

    /** 参保状态 code。 */
    private String psnInsuStas;

    /** 参保日期。 */
    private String psnInsuDate;

    /** 暂停参保日期。 */
    private String pausInsuDate;

    /** 公务员标志。 */
    private String cvlservFlag;

    /** 单位名称。 */
    private String empName;

    /** 个人账户余额。 */
    private BigDecimal balc;
}

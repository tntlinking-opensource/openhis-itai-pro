/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 病案首页费用汇总快照（自动取值中间结构）。
 *
 * <p>总额与 4401 分项键写入 {@code extension_json}；分项键与术语 {@code mr_settlement_type_code} 对齐。</p>
 */
@Getter
@Setter
public class FeeSummarySnapshot {

    /** 医疗费总额（4401 medfee_sumamt）。 */
    private BigDecimal medfeeSumamt;

    /**
     * 费用分项：键为 4401 参数代码（如 {@code wm_fee}），值为金额。
     */
    private Map<String, BigDecimal> feeItems = new LinkedHashMap<>();
}

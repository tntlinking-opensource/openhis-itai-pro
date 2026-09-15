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
 * 病案首页费用汇总快照（落库 / DTO.fees / 4401·卫统·HQMS 同源）。
 *
 * <p>持久化于 {@code mrms_case_record_homepage.medfee_sumamt} / {@code selfpay_amt} /
 * {@code fee_items_json}；仅「重新统计」或首次空快照补种时按当前目录病案结算类型重算覆盖。
 * 分项键与术语 {@code mr_settlement_type_code}（1～28）对齐。</p>
 */
@Getter
@Setter
public class FeeSummarySnapshot {

    /** 医疗费总额（4401 medfee_sumamt）。 */
    private BigDecimal medfeeSumamt;

    /** 自付金额（4401 selfpay_amt；医保=个人负担，纯自费=总额）。 */
    private BigDecimal selfpayAmt;

    /**
     * 费用分项：键为 4401 参数代码（如 {@code wm_fee}），值为金额。
     */
    private Map<String, BigDecimal> feeItems = new LinkedHashMap<>();
}

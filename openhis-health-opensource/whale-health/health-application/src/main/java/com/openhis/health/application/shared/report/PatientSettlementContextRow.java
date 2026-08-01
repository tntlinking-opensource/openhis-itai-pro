/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.report;

import lombok.Getter;
import lombok.Setter;

/**
 * 报表「患者结算上下文」中性行：费用性质（fin_contract）+ 医保医疗类别（med_type）。
 *
 * <p>供费用/发药/耗材等报表 enrich；禁止依赖 Feature Request/Response/DTO。</p>
 */
@Getter
@Setter
public class PatientSettlementContextRow {

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）。 */
    private String feeNatureContractNo;

    /** 费用性质展示名（合同名称）。 */
    private String feeNatureDisplay;

    /** 医保医疗类别 code（{@code YbMedType#getCode()}）。 */
    private String medType;

    /** 医保医疗类别展示文案。 */
    private String medTypeDisplay;
}

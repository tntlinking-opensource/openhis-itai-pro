/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.mapper;

import lombok.Getter;
import lombok.Setter;

/**
 * 补费父锚点解析结果（写入 Request.based_on_table / based_on_id）。
 *
 * <p>由 {@link NurseStationInpatientBillingMapper#resolveBasedOn} 根据 orderType 映射物理表。</p>
 */
@Getter
@Setter
public class NurseStationPricingBasedOnPair {

    /** 上级医嘱来源表。 */
    private String basedOnTable;

    /** 上级医嘱来源主键。 */
    private Long basedOnId;
}

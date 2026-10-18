/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 药房发退药记录查询（AS-OP-PHA-004 / AS-IP-PHA-004）记录类型筛选项 code。
 */
public interface PharmacyDispenseRecordConstants {

    /** 全部：正向已发 + 退药已退。 */
    String RECORD_TYPE_ALL = "ALL";

    /** 仅发药：正向 {@code COMPLETED} 行。 */
    String RECORD_TYPE_DISPENSE = "DISPENSE";

    /** 仅退药：{@code dispense_enum=REFUND} 且 {@code REFUNDED} 行。 */
    String RECORD_TYPE_REFUND = "REFUND";

    /** 记录类型筛选项展示：全部。 */
    String RECORD_TYPE_ALL_LABEL = "全部";

    /** 记录类型筛选项展示：发药。 */
    String RECORD_TYPE_DISPENSE_LABEL = "发药";

    /** 记录类型筛选项展示：退药。 */
    String RECORD_TYPE_REFUND_LABEL = "退药";

    /** 发药人下拉预加载页大小（IF-OPR-01 / IF-IPR-01）。 */
    int DISPENSER_OPTION_PAGE_SIZE = 500;

    /** 药品关键字预解析 catalog 查询页大小上限。 */
    int DRUG_KEYWORD_RESOLVE_PAGE_SIZE = 1000;
}

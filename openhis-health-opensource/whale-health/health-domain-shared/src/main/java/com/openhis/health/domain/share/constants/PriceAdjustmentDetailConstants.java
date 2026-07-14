/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 调价明细只读查询语义码（AS-FIN-PRC-002 IF-PRC-DTL-01～03）。
 */
public interface PriceAdjustmentDetailConstants {

    /** 默认分页大小（主行列表）。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 项目关键字预解析单次目录扫描上限（与领域分页 Query {@code getBoundedPageSize()} 上限 1000 一致）。 */
    int KEYWORD_CATALOG_SCAN_PAGE_SIZE = 1000;

    /** 详情主行不存在或状态非已生效（IF-PRC-DTL-03）。 */
    String EX_PRC_DTL_101 = "EX-PRC-DTL-101";

    /** 生效时间区间非法（IF-PRC-DTL-02）。 */
    String EX_PRC_DTL_102 = "EX-PRC-DTL-102";

    /** 项目主数据无法解析（IF-PRC-DTL-02/03）。 */
    String EX_PRC_DTL_103 = "EX-PRC-DTL-103";

    /** 列表/详情查询执行失败（IF-PRC-DTL-01～03）。 */
    String EX_PRC_DTL_104 = "EX-PRC-DTL-104";
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 财务审批页（AS-FIN-APR-001）判别码与默认分页等常量。
 *
 * <p>供应类型持久化码仍以 {@link com.zxplat.domain.share.enums.SupplyType} 为准；
 * 本类仅承载应用层列表判别（全部/调价）与文档种类，禁止重复日期格式等通用字面量。</p>
 */
public interface FinanceApprovalConstants {

    /** 列表类型：全部（供应财务族 ∪ 调价）。 */
    String TYPE_ALL = "ALL";

    /** 列表/写路径判别：调价单（非 {@code SupplyType}）。 */
    String TYPE_PRICE_ADJUSTMENT = "PRICE_ADJUSTMENT";

    /** 文档种类：供应请求财务族。 */
    String DOCUMENT_KIND_SUPPLY = "SUPPLY";

    /** 文档种类：调价单。 */
    String DOCUMENT_KIND_PRICE_ADJUSTMENT = "PRICE_ADJUSTMENT";

    /** 默认页大小（BR-APR-006 旁路；与库存单据列表同级）。 */
    int DEFAULT_PAGE_SIZE = 50;

    /** 品类选项展示：药品。 */
    String ITEM_TABLE_TEXT_MEDICATION = "药品";

    /** 品类选项展示：耗材。 */
    String ITEM_TABLE_TEXT_DEVICE = "耗材";

    /** 品类选项展示：诊疗活动（调价 clinical）。 */
    String ITEM_TABLE_TEXT_ACTIVITY = "诊疗活动";

    /** 品类选项展示：医疗服务（调价 clinical）。 */
    String ITEM_TABLE_TEXT_HEALTHCARE_SERVICE = "医疗服务";

    /** 统一审批状态展示：待审。 */
    String STATUS_TEXT_PENDING = "待审";

    /** 统一审批状态展示：已同意/已生效。 */
    String STATUS_TEXT_AGREED = "已同意";

    /** 统一审批状态展示：已驳回。 */
    String STATUS_TEXT_REJECTED = "已驳回";

    /** 类型选项展示：全部。 */
    String TYPE_TEXT_ALL = "全部";

    /** 类型选项展示：调价单。 */
    String TYPE_TEXT_PRICE_ADJUSTMENT = "调价单";
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.query;

import lombok.Getter;
import lombok.Setter;

/** 产品定义医保目录编码读侧投影（跨聚合批量加载）。 */
@Getter
@Setter
public class ProductDefinitionCatalogQueryModel {

    private String productTableName;
    private Long productDefinitionId;
    private String productBusNo;
    /** 产品名称（收费项缺码提示等展示用）。 */
    private String productName;
    /** 国家医保目录编码（2204 med_list_codg）。 */
    private String ybNo;
    /**
     * 医药机构目录编码（药品/诊疗 2204 medins_list_codg 优先来源；耗材已废弃院内 yb_org_no，结算用完整 yb_no）。
     * <p>为空时回落 {@link #productBusNo}。</p>
     */
    private String ybOrgNo;
}

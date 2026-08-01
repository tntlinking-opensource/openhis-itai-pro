/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.command;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 医保文件运行时门禁入参（开单/2204/2301 消费）。
 */
@Data
public class ComplianceGateCommand {

    /** 租户。 */
    private Long tenantId;

    /** 契约；可与 overlay.contract_id=0 回退匹配。 */
    private Long contractId;

    /** 目录类别（可选；空则按 hilist 跨类别匹配）。 */
    private String listType;

    /** 国家目录编码（med_list_codg / yb_no）。 */
    private String hilistCode;

    /** 物价/项目代码（可选；用于 C-PROJECT-UNMAPPED / 甲乙查找）。 */
    private String projectCode;

    /** 计价单位单价；空则跳过限价比较。 */
    private BigDecimal unitPrice;

    /** 院内产品当前 chrgitm_lv（可选；空则由 Gate 经 Port 回查）。 */
    private String productChrgitmLv;

    /** 判定日；空则 today。 */
    private LocalDate asOfDate;

    /** 收费项目展示名（可选；用于友好提示）。 */
    private String itemDisplayName;

    /** 院内产品定义主键（可选；前端跳转补码）。 */
    private Long productDefinitionId;

    /** 产品表名（可选；如 wkf_activity_definition）。 */
    private String productTableName;
}

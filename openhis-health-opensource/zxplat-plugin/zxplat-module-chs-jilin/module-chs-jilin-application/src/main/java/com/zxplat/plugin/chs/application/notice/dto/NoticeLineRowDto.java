/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 通知明细行展示 DTO（预览样例与明细分页共用）。
 */
@Data
public class NoticeLineRowDto {

    /** 动作。 */
    private String action;

    /** 国家目录编码。 */
    private String hilistCode;

    /** 物价项目代码。 */
    private String projectCode;

    /** 名称。 */
    private String hilistName;

    /** 行生效起日。 */
    private LocalDate beginDate;

    /** 行终止日。 */
    private LocalDate endDate;

    /** 收费项目等级。 */
    private String chrgitmLv;

    /** 定价上限金额。 */
    private BigDecimal priceLimitAmt;

    /** 来源 sheet。 */
    private String sheetName;

    /** 原表行号。 */
    private Integer rowNo;

    /** 行级警告。 */
    private String warning;

    /** 与镜像对照状态。 */
    private String matchStatus;

    /** 应用结果。 */
    private String applyResult;
}

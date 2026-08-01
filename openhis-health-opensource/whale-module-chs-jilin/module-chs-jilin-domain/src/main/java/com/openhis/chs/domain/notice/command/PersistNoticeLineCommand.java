/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.command;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 确认入库时物化的单行命令。
 */
@Data
public class PersistNoticeLineCommand {

    /** 动作。 */
    private String action;

    /** 目录类别。 */
    private String listType;

    /** 国家/医疗目录编码。 */
    private String hilistCode;

    /** 物价项目代码。 */
    private String projectCode;

    /** 名称。 */
    private String hilistName;

    /** 行生效起日。 */
    private LocalDate beginDate;

    /** 行终止日。 */
    private LocalDate endDate;

    /** 本期变更字段说明。 */
    private String changeFields;

    /** 收费项目等级。 */
    private String chrgitmLv;

    /** 限制使用标志。 */
    private String lmtUsedFlag;

    /** 限制使用范围。 */
    private String lmtScope;

    /** 定价上限金额。 */
    private BigDecimal priceLimitAmt;

    /** 超限处理方式。 */
    private String overlmtDspoWay;

    /** 双通道标志。 */
    private String dualChannelFlag;

    /** 整行 JSON。 */
    private String payloadJson;

    /** 来源 sheet。 */
    private String sheetName;

    /** 原表行号。 */
    private Integer rowNo;

    /** 行级警告。 */
    private String warningMessage;
}

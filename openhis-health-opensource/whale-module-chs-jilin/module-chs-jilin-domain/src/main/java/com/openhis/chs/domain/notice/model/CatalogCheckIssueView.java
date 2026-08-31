/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 目录检查问题行（只读）。
 */
@Data
public class CatalogCheckIssueView {
    /** 问题码。 */
    private String issueCode;
    /** 问题中文。 */
    private String issueLabel;
    /** 说明。 */
    private String message;
    /** ACTIVITY / MEDICATION / DEVICE。 */
    private String productKind;
    /** 产品表名。 */
    private String productTable;
    /** 产品主键。 */
    private Long productId;
    /** 院内编码。 */
    private String projectCode;
    /** 项目名称。 */
    private String productName;
    /** 国家医保编码。 */
    private String hilistCode;
    /** 国家目录结束日。 */
    private LocalDate enddate;
    /** 有效标志。 */
    private String valiFlag;
    /** 院内售价。 */
    private BigDecimal salePrice;
    /** 医保限价。 */
    private BigDecimal limitAmt;
}

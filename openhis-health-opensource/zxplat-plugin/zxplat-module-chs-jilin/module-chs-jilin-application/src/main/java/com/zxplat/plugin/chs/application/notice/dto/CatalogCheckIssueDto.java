/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 目录检查问题行。
 */
@Data
public class CatalogCheckIssueDto {
    private String issueCode;
    private String issueLabel;
    private String message;
    private String productKind;
    private String productTable;
    private Long productId;
    private String projectCode;
    private String productName;
    private String hilistCode;
    private LocalDate enddate;
    private String valiFlag;
    private BigDecimal salePrice;
    private BigDecimal limitAmt;
}

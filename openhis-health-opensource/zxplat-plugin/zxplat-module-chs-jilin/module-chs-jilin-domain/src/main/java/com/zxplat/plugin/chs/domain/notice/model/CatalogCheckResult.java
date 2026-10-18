/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录检查只读结果。
 */
@Data
public class CatalogCheckResult {
    /** 判定日。 */
    private LocalDate asOf;
    /** 扫描的院内启用医保项数。 */
    private int scannedCount;
    /** 本地国家目录镜像行数（租户）。 */
    private long catalogItemCount;
    /** 问题行数。 */
    private int issueCount;
    private int expiredCount;
    private int disabledCount;
    private int noticeStopCount;
    /** 局文有效期已过。 */
    private int noticeExpiredCount;
    /** 局文尚未生效。 */
    private int noticeNotEffectiveCount;
    private int priceOverCount;
    private int missingYbNoCount;
    private int notInCatalogCount;
    /** 问题明细。 */
    private List<CatalogCheckIssueView> issues = new ArrayList<>();
}

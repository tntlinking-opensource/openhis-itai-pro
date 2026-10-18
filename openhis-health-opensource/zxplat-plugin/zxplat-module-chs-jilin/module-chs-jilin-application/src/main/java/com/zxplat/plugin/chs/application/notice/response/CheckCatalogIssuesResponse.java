/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import com.zxplat.plugin.chs.application.notice.dto.CatalogCheckIssueDto;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录检查只读响应。
 */
@Data
public class CheckCatalogIssuesResponse {
    private LocalDate asOf;
    private int scannedCount;
    private long catalogItemCount;
    private int issueCount;
    private int expiredCount;
    private int disabledCount;
    private int noticeStopCount;
    private int noticeExpiredCount;
    private int noticeNotEffectiveCount;
    private int priceOverCount;
    private int missingYbNoCount;
    private int notInCatalogCount;
    private List<CatalogCheckIssueDto> issues = new ArrayList<>();
}

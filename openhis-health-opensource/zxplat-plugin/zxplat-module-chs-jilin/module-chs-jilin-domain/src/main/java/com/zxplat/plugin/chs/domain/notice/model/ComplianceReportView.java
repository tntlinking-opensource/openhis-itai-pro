/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 最新合规报告领域视图（供应用层 R-03 装配）。
 */
@Data
public class ComplianceReportView {
    /** 报告主键。 */
    private Long reportId;
    /** 跑批时过滤的契约；可空。 */
    private Long contractId;
    /** 跑批时过滤的目录类别；可空。 */
    private String listType;
    /**
     * 落库摘要 JSON（契约字段，与实体 summary_json 一致）。
     * 应用层可原样透出；问题明细见 {@link #issues}。
     */
    private String summaryJson;
    /** 问题条数。 */
    private Integer issueCount;
    /** 跑批时间。 */
    private LocalDateTime ranAt;
    /** 结构化问题列表。 */
    private List<ComplianceIssueView> issues = new ArrayList<>();
}

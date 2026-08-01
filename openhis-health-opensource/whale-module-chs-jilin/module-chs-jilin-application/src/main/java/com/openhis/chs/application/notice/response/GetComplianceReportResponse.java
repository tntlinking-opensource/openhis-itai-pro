/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import com.openhis.chs.application.notice.dto.ComplianceIssueDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 合规报告响应（IF-CHS-CAT-005-R-03）。
 */
@Data
public class GetComplianceReportResponse {
    /** 报告主键 {@code yb_catalog_notice_compliance_report.id}。 */
    private Long reportId;
    /** 跑批完成时间。 */
    private LocalDateTime ranAt;
    /**
     * 报告摘要 JSON 字符串（SDD 契约字段：issueCount/overlayCount/listType/contractId）。
     * <p>勿再扩展其它 JSON 透传；明细问题见 {@link #issues} 结构化列表。</p>
     */
    private String summaryJson;
    /** 问题条数（与 issues 长度一致，便于列表摘要）。 */
    private Integer issueCount;
    /** 结构化问题列表（门禁码/编码/文案/严重度）。 */
    private List<ComplianceIssueDto> issues = new ArrayList<>();
}

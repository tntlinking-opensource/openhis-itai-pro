/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保文件通知合规校验报告（{@code yb_catalog_notice_compliance_report}）。
 * <p>issues JSON 体积大且会覆盖写入，排除框架审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_notice_compliance_report")
public class YbCatalogNoticeComplianceReport extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 契约主键；可空表示租户级全量。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** 目录类别过滤。 */
    @Column(name = "list_type", length = 30)
    private String listType;

    /**
     * 报告摘要 JSON（落库契约字段：issueCount/overlayCount/listType/contractId）。
     * 出参可原样透出；勿再扩其它透传用途。
     */
    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    /** 问题明细 JSON（读路径反序列化为结构化 issues，不直接 HTTP 透出）。 */
    @Column(name = "issues_json", columnDefinition = "TEXT")
    private String issuesJson;

    /** 问题条数。 */
    @Column(name = "issue_count", nullable = false)
    private Integer issueCount = 0;

    /** 校验执行时间。 */
    @Column(name = "ran_at", nullable = false)
    private LocalDateTime ranAt;

    /** 执行人。 */
    @Column(name = "ran_by")
    private Long ranBy;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.reporttemplate.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查报告正文模板（{@code pacs_report_template}）。
 *
 * <p>按模态维护所见/结论样本文本，供书写台套用（DM-PACS-004）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_report_template")
public class PacsReportTemplate extends BaseAuditedEntity {

    /** 模板名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 模态码；空表示通用。 */
    @Column(name = "modality_code")
    private String modalityCode;

    /** 所见样本文本。 */
    @Column(name = "findings")
    private String findings;

    /** 结论样本文本。 */
    @Column(name = "conclusion")
    private String conclusion;

    /** 是否启用。 */
    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;

    /** 列表排序。 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 个人模板归属；空表示科室/院级。 */
    @Column(name = "owner_practitioner_id")
    private Long ownerPractitionerId;
}

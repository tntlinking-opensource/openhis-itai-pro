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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医保文件通知催办待办（{@code yb_catalog_notice_todo}）。
 * <p>待办重算批量 upsert，排除框架审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_notice_todo")
public class YbCatalogNoticeTodo extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 待办类型：STOP / PAY_CLASS。 */
    @Column(name = "todo_type", nullable = false, length = 32)
    private String todoType;

    /** 国家目录编码。 */
    @Column(name = "hilist_code", length = 64)
    private String hilistCode;

    /** 物价项目代码。 */
    @Column(name = "project_code", length = 64)
    private String projectCode;

    /** 院内/目录项名称展示。 */
    @Column(name = "product_name", length = 512)
    private String productName;

    /** 生效日。 */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** 来源文件头。 */
    @Column(name = "doc_id")
    private Long docId;

    /** 来源明细行。 */
    @Column(name = "line_id")
    private Long lineId;

    /** 状态：OPEN / CLOSED。 */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 关闭原因。 */
    @Column(name = "close_reason", length = 512)
    private String closeReason;

    /** 关闭人。 */
    @Column(name = "closed_by")
    private Long closedBy;

    /** 关闭时间。 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家目录通知叠加卫星表（{@code yb_national_catalog_notice_overlay}）。
 * <p>业务键：tenant + contract + list_type + hilist_code。
 * 应用批次量大，排除框架审计；变更以 {@code yb_catalog_notice_apply_log} 为准。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_national_catalog_notice_overlay")
public class YbNationalCatalogNoticeOverlay extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 契约主键。 */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 目录类别。 */
    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    /** 国家目录编码。 */
    @Column(name = "hilist_code", nullable = false, length = 64)
    private String hilistCode;

    /** 通知口径可用：1 可用 / 0 停用（列名对齐方案 notice_avali_flag）。 */
    @Column(name = "notice_avali_flag", length = 8)
    private String noticeAvaliFlag;

    /** 通知终止日。 */
    @Column(name = "notice_end_date")
    private LocalDate noticeEndDate;

    /** 补齐字段 JSON（等级、限价等）。 */
    @Column(name = "notice_patch_json", columnDefinition = "TEXT")
    private String noticePatchJson;

    /** 最近来源文件头。 */
    @Column(name = "last_notice_doc_id")
    private Long lastNoticeDocId;

    /** 最近来源明细行。 */
    @Column(name = "last_notice_line_id")
    private Long lastNoticeLineId;

    /** 叠加生效时间。 */
    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;
}

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

import java.time.LocalDateTime;

/**
 * 医保文件通知应用审计（{@code yb_catalog_notice_apply_log}）。
 * <p>本表即业务审计轨迹，不再写入框架审计日志（避免对审计表再审计）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_notice_apply_log")
public class YbCatalogNoticeApplyLog extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 文件头主键。 */
    @Column(name = "doc_id", nullable = false)
    private Long docId;

    /** 明细行主键。 */
    @Column(name = "line_id")
    private Long lineId;

    /** 被改目标表名。 */
    @Column(name = "target_table", length = 64)
    private String targetTable;

    /** 被改目标行主键。 */
    @Column(name = "target_id")
    private Long targetId;

    /** 变更前快照 JSON。 */
    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    /** 变更后快照 JSON。 */
    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    /** 操作类型：PATCH_FIELD/SET_UNAVAILABLE/WRITEBACK_CHRGITM_LV 等。 */
    @Column(name = "op", nullable = false, length = 64)
    private String op;

    /** 关联单据引用 JSON（未收费清单等）。 */
    @Column(name = "affected_refs_json", columnDefinition = "TEXT")
    private String affectedRefsJson;

    /** 操作人。 */
    @Column(name = "operator_id")
    private Long operatorId;

    /** 操作时间。 */
    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;
}

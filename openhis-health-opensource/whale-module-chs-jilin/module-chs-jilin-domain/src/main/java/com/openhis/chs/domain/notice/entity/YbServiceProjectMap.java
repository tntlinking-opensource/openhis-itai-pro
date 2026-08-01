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
 * 01* 项目码与国家目录编码映射（{@code yb_service_project_map}）。
 * <p>应用支付对照时可能批量写入，排除框架审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_service_project_map")
public class YbServiceProjectMap extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 物价/项目代码（01*）。 */
    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    /** 国家目录编码；PENDING 时可空。 */
    @Column(name = "hilist_code", length = 64)
    private String hilistCode;

    /** 映射状态：MAPPED/PENDING。 */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 来源通知文件头。 */
    @Column(name = "notice_doc_id")
    private Long noticeDocId;

    /** 来源通知明细行。 */
    @Column(name = "notice_line_id")
    private Long noticeLineId;

    /** 映射完成时间。 */
    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;
}

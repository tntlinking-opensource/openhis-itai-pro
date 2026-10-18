/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.framework.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 1901 字典手动同步日志；本表即同步轨迹，排除框架审计。 */
@Getter
@Setter
@Entity
@Table(name = "yb_platform_dict_sync_log")
public class YbPlatformDictSyncLog extends BaseAuditedEntity implements IExcludeFromAudit {

    @Column(name = "dict_type", nullable = false, length = 30)
    private String dictType;

    @Column(name = "query_admdvs", length = 6)
    private String queryAdmdvs;

    @Column(name = "parent_value", length = 20)
    private String parentValue;

    @Column(name = "recursive", nullable = false)
    private Boolean recursive;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "rows_upserted", nullable = false)
    private Integer rowsUpserted;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}

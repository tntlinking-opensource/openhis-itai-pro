/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 明细审核 3103 反馈行。
 *
 * <p>{@code warn_rslt_id} 存明细 {@code jrd_id}（产品 R7=B）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_detail_audit_feedback")
public class YbDetailAuditFeedback extends BaseAuditedEntity {

    /** 提醒结果 ID（= jrd_id） */
    @Column(name = "warn_rslt_id", nullable = false, length = 64)
    private String warnRsltId;

    /** 处置方式 */
    @Column(name = "dspo_way", nullable = false, length = 8)
    private String dspoWay;

    /** 处置原因 */
    @Column(name = "dspo_way_rea", length = 512)
    private String dspoWayRea;

    /** 提醒类型 */
    @Column(name = "warn_type", length = 8)
    private String warnType;

    /** 关联违规头（冗余） */
    @Column(name = "jr_id", length = 64)
    private String jrId;

    /** 契约 bus_no */
    @Column(name = "contract_bus_no", length = 64)
    private String contractBusNo;

    /** 操作员 ID */
    @Column(name = "operator_id")
    private Long operatorId;
}

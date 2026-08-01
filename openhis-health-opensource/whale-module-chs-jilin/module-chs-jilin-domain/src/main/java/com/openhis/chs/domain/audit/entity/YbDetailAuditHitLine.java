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

import java.math.BigDecimal;

/**
 * 明细审核违规明细行（对应 {@code judge_result_detail_dtos[]}）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_detail_audit_hit_line")
public class YbDetailAuditHitLine extends BaseAuditedEntity {

    /** 明细结果 ID（平台 jrd_id，租户内唯一；3103 warn_rslt_id） */
    @Column(name = "jrd_id", nullable = false, length = 64)
    private String jrdId;

    /** 违规头 jr_id */
    @Column(name = "jr_id", nullable = false, length = 64)
    private String jrId;

    /** 处方/医嘱 ID */
    @Column(name = "rx_id", length = 64)
    private String rxId;

    /** 违规项目类型 */
    @Column(name = "vola_item_type", length = 8)
    private String volaItemType;

    /** 违规金额 */
    @Column(name = "vola_amt", precision = 18, scale = 4)
    private BigDecimal volaAmt;

    /** 就诊 ID（冗余） */
    @Column(name = "mdtrt_id", length = 64)
    private String mdtrtId;
}

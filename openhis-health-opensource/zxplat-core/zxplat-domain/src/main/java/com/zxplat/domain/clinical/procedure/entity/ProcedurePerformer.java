/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行人子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_procedure_performer")
public class ProcedurePerformer extends BaseAuditedEntity {
    @Column(name = "procedure_id", nullable = false)
    /** 操作主键。 */
    private Long procedureId;
    @Column(name = "function_code")
    /** 职能编码。 */
    private String functionCode;
    @Column(name = "start_time", nullable = false)
    /** 开始时间。 */
    private LocalDateTime startTime;
    @Column(name = "end_time")
    /** 结束时间。 */
    private LocalDateTime endTime;
    @Column(name = "practitioner_id", nullable = false)
    /** 执业者主键。 */
    private Long practitionerId;
    @Column(name = "org_from_id")
    /** 机构来源主键。 */
    private String orgFromId;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行人查询模型。
 */
@Getter
@Setter
public class ProcedurePerformerQueryModel {
    /** 主键。 */
    private Long id;
    /** 操作主键。 */
    private Long procedureId;
    /** 执业者主键。 */
    private Long practitionerId;
    /** 职能编码。 */
    private String functionCode;
    /** 开始时间。 */
    private LocalDateTime startTime;
    /** 结束时间。 */
    private LocalDateTime endTime;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行记录投影查询模型。
 */
@Getter
@Setter
public class ProcedureProjectionQueryModel {
    /** 操作主键。 */
    private Long procedureId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
}

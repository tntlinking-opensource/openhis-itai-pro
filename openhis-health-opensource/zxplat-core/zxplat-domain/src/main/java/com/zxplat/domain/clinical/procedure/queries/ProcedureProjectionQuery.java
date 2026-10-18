/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录投影查询入参。
 */
@Getter
@Setter
public class ProcedureProjectionQuery {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 类别枚举编码。 */
    private String categoryEnum;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录可完成性校验模型。
 */
@Getter
@Setter
public class ProcedureCompletableCheckQueryModel {
    /** 操作主键。 */
    private Long procedureId;
    /** pass。 */
    private Boolean pass;
    /** fail编码。 */
    private String failCode;
    /** fail原因。 */
    private String failReason;
    /** 状态枚举编码。 */
    private String statusEnum;
}

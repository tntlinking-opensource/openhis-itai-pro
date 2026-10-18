/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 执行记录详情模型。
 */
@Getter
@Setter
/**
 * ProcedureDetailQueryModel。
 */
public class ProcedureDetailQueryModel {
    /** 操作。 */
    /**
     * procedure。
     */
    private ProcedureRecordQueryModel procedure;
    /** performers。 */
    /**
     * performers。
     */
    private List<ProcedurePerformerQueryModel> performers;
}

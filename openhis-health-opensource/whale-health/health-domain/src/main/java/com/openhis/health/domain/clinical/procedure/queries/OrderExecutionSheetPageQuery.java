/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医嘱执行单分页入参（查 {@code cln_procedure}；替代 RM {@code OrderExecutionSheetPageQuery}）。
 */
@Getter
@Setter
public class OrderExecutionSheetPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 执行记录主键 {@code cln_procedure.id}（精确查单条时）。 */
    private Long procedureId;

    /** 来源医嘱/申请主键 {@code cln_procedure.request_id}。 */
    private Long requestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 执行状态持久化码。 */
    private String statusEnum;

    /** 诊疗类目持久化码。 */
    private String categoryEnum;

    /** 关联退费单主键（退药/退执行场景）。 */
    private Long refundId;

    /** 执行时间窗下限（含）。 */
    private LocalDateTime startTime;

    /** 执行时间窗上限（含）。 */
    private LocalDateTime endTime;
}

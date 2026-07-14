/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.response;

import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentQueueRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;

/**
 * IF-IP-TRC-01：分页查询待追溯分配患者队列出参（B.10.11）。
 *
 * <p>继承健康域 {@link PagedResult}：{@code content}、{@code totalCount}、{@code pageNo}、{@code pageSize}。</p>
 */
public class PagePatientsPendingTraceAssignmentResponse extends PagedResult<PatientTraceAssignmentQueueRowDto> {
}

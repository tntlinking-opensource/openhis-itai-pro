/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-DOR-07：诊断定义分页响应。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为诊断定义行数据。</p>
 */
public class ConditionDefinitionPageResponse extends PagedResult<ConditionDefinitionRowResponse> {
}

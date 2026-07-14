/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-DOR-04：诊断定义分页响应（AS-IP-DOR-002）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为当前页 {@link ConditionDefinitionRowResponse} 行。</p>
 */
public class ConditionDefinitionPageResponse extends PagedResult<ConditionDefinitionRowResponse> {
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.response;

import com.zxplat.application.outpatient.nursestation.treatment.dto.OrderExecutionSheetItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 分页查询诊疗执行记录响应（IF-NUR-03）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为执行记录行数据。</p>
 */
public class ListProcedureRecordsForNurseStationResponse extends PagedResult<OrderExecutionSheetItemDto> {
}

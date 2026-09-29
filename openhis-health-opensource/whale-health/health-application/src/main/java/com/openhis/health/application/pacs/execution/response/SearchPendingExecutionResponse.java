/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.response;

import com.openhis.health.application.pacs.execution.dto.PacsExecutionRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 待摄列表响应。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingExecutionResponse extends PagedResult<PacsExecutionRowDto> {
}

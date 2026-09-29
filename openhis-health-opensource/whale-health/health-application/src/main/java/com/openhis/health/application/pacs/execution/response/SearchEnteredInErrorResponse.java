/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.execution.response;

import com.openhis.health.application.pacs.execution.dto.PacsExecutionRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 已作废列表响应（IF-PACS-EXE-04）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查执行-已作废列表响应")
public class SearchEnteredInErrorResponse extends PagedResult<PacsExecutionRowDto> {

    private static final long serialVersionUID = 1L;
}

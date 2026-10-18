/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.execution.response;

import com.zxplat.plugin.pacs.application.execution.dto.PacsExecutionRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 他科已检列表响应（IF-PACS-EXE-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查执行-他科已检列表响应")
public class SearchCompletedOtherDeptResponse extends PagedResult<PacsExecutionRowDto> {

    private static final long serialVersionUID = 1L;
}

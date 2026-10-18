/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.response;

import com.zxplat.plugin.peis.application.history.dto.ExamHistoryRowDto;
import com.zxplat.framework.core.application.paging.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检记录分页响应。
 */
@Getter
@Setter
public class PageExamRecordsResponse extends PagedResult<ExamHistoryRowDto> {
}

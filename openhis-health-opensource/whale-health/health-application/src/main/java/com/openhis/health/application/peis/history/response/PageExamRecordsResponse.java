/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.history.response;

import com.openhis.health.application.peis.history.dto.ExamHistoryRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检记录分页响应。
 */
@Getter
@Setter
public class PageExamRecordsResponse extends PagedResult<ExamHistoryRowDto> {
}

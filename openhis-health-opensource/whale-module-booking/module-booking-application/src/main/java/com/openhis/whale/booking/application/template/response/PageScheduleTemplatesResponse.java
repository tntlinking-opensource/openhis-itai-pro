/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * T2 模板分页响应。
 */
@Getter
@Setter
public class PageScheduleTemplatesResponse extends PagedResult<ScheduleTemplateDto> {
}

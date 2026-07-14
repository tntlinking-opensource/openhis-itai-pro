/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检名单分页响应（9.5）。
 */
@Getter
@Setter
public class PageGroupCheckupRostersResponse extends PagedResult<GroupCheckupRosterDto> {
}

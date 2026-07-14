/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检批次分页响应（6.x 旧版 API 别名，对应 PageGroupCheckupBatchesResponse）。
 */
@Getter
@Setter
public class PageGroupBatchesResponse extends PagedResult<GroupBatchDto> {
}

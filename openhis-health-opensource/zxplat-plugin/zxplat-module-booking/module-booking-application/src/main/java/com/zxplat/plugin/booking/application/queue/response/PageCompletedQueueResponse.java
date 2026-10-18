/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 已完成候诊分页响应。
 */
@Getter
@Setter
public class PageCompletedQueueResponse extends PagedResult<QueueListItemDto> {
}

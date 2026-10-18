/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.queue.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 候诊队列分页响应（5.6）。
 */
@Getter
@Setter
public class PageQueueResponse extends PagedResult<QueueListItemDto> {
}

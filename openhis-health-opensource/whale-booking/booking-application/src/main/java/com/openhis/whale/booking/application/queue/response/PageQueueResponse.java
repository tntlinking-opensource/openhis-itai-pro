package com.openhis.whale.booking.application.queue.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 候诊队列分页响应（5.6）。
 */
@Getter
@Setter
public class PageQueueResponse extends PagedResult<QueueListItemDto> {
}

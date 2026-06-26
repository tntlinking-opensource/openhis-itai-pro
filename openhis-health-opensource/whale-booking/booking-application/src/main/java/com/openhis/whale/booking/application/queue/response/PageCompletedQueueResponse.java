package com.openhis.whale.booking.application.queue.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 已完成候诊分页响应。
 */
@Getter
@Setter
public class PageCompletedQueueResponse extends PagedResult<QueueListItemDto> {
}

package com.openhis.whale.booking.application.register.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 预约列表分页响应（4.5）。
 */
@Getter
@Setter
public class PageBookingsResponse extends PagedResult<BookingListItemDto> {
}

package com.openhis.whale.booking.application.register.response;

import com.openhis.whale.booking.contracts.dto.BookingPatientItem;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者检索分页响应（4.7）。
 */
@Getter
@Setter
public class PagePatientsForSelectResponse extends PagedResult<BookingPatientItem> {
}

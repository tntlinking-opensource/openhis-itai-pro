package com.openhis.whale.booking.application.healthcheckup.response;

import com.openhis.whale.booking.application.register.response.BookableSlotDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 查询个检可约号源响应（6.x 旧版 API 别名）。
 */
@Getter
@Setter
public class ListIndividualBookableSlotsResponse {

    /** 可约号源列表。 */
    private List<BookableSlotDto> slots;
}

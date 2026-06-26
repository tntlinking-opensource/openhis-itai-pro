package com.openhis.whale.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 可预约医嘱列表响应。
 */
@Getter
@Setter
public class ListBookableServiceRequestsResponse {

    /** 可预约医嘱列表。 */
    private List<ServiceRequestItemDto> items;
}

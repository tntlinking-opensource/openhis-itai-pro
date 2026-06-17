package com.openhis.whale.booking.application.bed.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预约台初始化请求（8.1）。
 */
@Getter
@Setter
public class LoadBedBookingFormInitRequest {

    private Long organizationId;
}

package com.openhis.whale.booking.application.bed.response;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 床位预约台初始化响应（8.1）。
 */
@Getter
@Setter
public class LoadBedBookingFormInitResponse {

    private List<OptionDto> wards = new ArrayList<>();
}

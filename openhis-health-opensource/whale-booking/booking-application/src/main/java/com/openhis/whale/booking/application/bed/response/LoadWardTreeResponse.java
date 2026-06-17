package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 病区树响应。
 */
@Getter
@Setter
public class LoadWardTreeResponse {

    private List<WardTreeNodeDto> wards;
}

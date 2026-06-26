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

    /** 病区树节点列表。 */
    private List<WardTreeNodeDto> wards;
}

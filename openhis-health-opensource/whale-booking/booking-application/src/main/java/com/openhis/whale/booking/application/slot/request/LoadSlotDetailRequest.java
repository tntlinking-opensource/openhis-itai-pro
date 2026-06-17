package com.openhis.whale.booking.application.slot.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 号源详情请求（3.2）。
 */
@Getter
@Setter
public class LoadSlotDetailRequest {

    /** 号源主键。 */
    @NotNull
    private Long slotId;
}

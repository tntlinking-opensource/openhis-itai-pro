package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消候补。
 */
@Getter
@Setter
public class CancelWaitlistRequest {

    @NotNull
    private Long waitlistEntryId;

    private String reason;
}

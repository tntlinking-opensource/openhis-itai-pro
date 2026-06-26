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

    /** 候补条目主键。 */
    @NotNull
    private Long waitlistEntryId;

    /** 取消原因（可选）。 */
    private String reason;
}

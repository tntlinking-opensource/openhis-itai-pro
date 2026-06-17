package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加入候补队列（BR-EXT-011）。
 */
@Getter
@Setter
public class JoinWaitlistRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long patientId;

    @NotNull
    private Long slotId;
}

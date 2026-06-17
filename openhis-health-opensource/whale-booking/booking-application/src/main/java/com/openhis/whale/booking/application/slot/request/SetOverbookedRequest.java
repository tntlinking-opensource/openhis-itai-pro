package com.openhis.whale.booking.application.slot.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetOverbookedRequest {

    @NotNull
    private Long slotId;

    @NotNull
    private Boolean overbooked;
}

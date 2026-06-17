package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DragChangeOrderRequest {

    @NotNull
    private Long appointmentId;

    @NotNull
    private Integer targetIndex;
}

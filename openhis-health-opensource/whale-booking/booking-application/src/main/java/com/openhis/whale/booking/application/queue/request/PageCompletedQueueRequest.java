package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageCompletedQueueRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long destinationLocationId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}

package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetQueueDisplaySnapshotRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long destinationLocationId;
}

package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量预约请求（9.6）。
 */
@Getter
@Setter
public class BatchCreateHealthCheckupBookingRequest {

    @NotNull
    private Long batchId;

    private List<Long> rosterIds = new ArrayList<>();
}

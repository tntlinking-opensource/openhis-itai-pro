package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BatchBookGroupRosterRequest {

    @NotNull
    private Long batchId;

    private List<Long> rosterIds;
}

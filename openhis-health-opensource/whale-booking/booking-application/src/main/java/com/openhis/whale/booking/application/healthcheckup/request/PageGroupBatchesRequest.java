package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageGroupBatchesRequest {

    private Long groupOrgId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}

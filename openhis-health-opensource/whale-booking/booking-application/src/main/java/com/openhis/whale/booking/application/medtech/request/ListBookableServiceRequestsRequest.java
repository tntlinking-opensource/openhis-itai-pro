package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 可预约医嘱列表请求。
 */
@Getter
@Setter
public class ListBookableServiceRequestsRequest {

    @NotNull
    private Long organizationId;

    @NotNull
    private Long encounterId;
}

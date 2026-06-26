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

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 就诊主键。 */
    @NotNull
    private Long encounterId;
}

package com.openhis.whale.booking.application.queue.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 已完成候诊分页请求。
 */
@Getter
@Setter
public class PageCompletedQueueRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 诊室/去向地点主键。 */
    @NotNull
    private Long destinationLocationId;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}

package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 放号策略分页请求。
 */
@Getter
@Setter
public class PageReleasePoliciesRequest {

    /** 科室/机构主键；空=全院。 */
    private Long organizationId;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}

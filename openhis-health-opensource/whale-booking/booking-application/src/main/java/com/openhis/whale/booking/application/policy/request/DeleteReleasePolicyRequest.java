package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除放号策略请求。
 */
@Getter
@Setter
public class DeleteReleasePolicyRequest {

    /** 放号策略主键。 */
    @NotNull
    private Long id;
}

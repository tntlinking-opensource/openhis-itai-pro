package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 病区树加载请求。
 */
@Getter
@Setter
public class LoadWardTreeRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;
}

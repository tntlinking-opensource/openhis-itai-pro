package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除体检套餐请求（9.1）。
 */
@Getter
@Setter
public class DeleteHealthCheckPackageRequest {

    @NotNull
    private Long id;
}

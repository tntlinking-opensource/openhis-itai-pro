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

    @NotNull
    private Long organizationId;
}

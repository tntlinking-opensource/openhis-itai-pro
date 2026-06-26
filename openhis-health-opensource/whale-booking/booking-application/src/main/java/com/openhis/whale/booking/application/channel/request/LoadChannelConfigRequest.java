package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加载渠道配置请求（10.1）。
 */
@Getter
@Setter
public class LoadChannelConfigRequest {

    /** 渠道配置主键。 */
    @NotNull
    private Long id;
}

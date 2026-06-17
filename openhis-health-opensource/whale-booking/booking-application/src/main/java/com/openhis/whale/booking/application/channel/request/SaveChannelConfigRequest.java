package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveChannelConfigRequest {

    private Long id;
    private Long organizationId;

    @NotBlank
    private String channelCode;

    @NotBlank
    private String channelName;

    private Boolean enabledFlag;
    private Integer quotaPercent;
    private String remarkText;
}

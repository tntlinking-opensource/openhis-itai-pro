package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelConfigDto {

    private Long id;
    private String channelCode;
    private String channelName;
    private Boolean enabledFlag;
    private Integer quotaPercent;
    private String remarkText;
}

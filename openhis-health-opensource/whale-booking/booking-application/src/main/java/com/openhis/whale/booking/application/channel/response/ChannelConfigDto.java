package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置 DTO（10.1）。
 */
@Getter
@Setter
public class ChannelConfigDto {

    /** 配置主键。 */
    private Long id;

    /** 渠道编码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 是否启用。 */
    private Boolean enabledFlag;

    /** 号源配额百分比。 */
    private Integer quotaPercent;

    /** 备注。 */
    private String remarkText;
}

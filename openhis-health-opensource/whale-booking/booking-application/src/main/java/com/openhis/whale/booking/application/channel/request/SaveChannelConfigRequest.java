package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存渠道配置请求（10.1）。
 */
@Getter
@Setter
public class SaveChannelConfigRequest {

    /** 配置主键（更新时传入）。 */
    private Long id;

    /** 机构/科室主键。 */
    private Long organizationId;

    /** 渠道编码。 */
    @NotBlank
    private String channelCode;

    /** 渠道名称。 */
    @NotBlank
    private String channelName;

    /** 是否启用。 */
    private Boolean enabledFlag;

    /** 号源配额百分比。 */
    private Integer quotaPercent;

    /** 备注。 */
    private String remarkText;
}

package com.openhis.whale.booking.application.channel.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置分页响应（10.1）。
 */
@Getter
@Setter
public class PageChannelConfigsResponse extends PagedResult<ChannelConfigDto> {
}

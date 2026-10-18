/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.channel.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置分页响应（10.1）。
 */
@Getter
@Setter
public class PageChannelConfigsResponse extends PagedResult<ChannelConfigDto> {
}

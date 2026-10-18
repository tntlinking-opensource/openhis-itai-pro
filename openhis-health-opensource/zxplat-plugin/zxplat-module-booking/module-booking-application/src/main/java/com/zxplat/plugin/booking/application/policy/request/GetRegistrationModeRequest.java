/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 加载院点挂号模式请求。
 */
@Getter
@Setter
public class GetRegistrationModeRequest {

    /** 科室/机构主键；空则仅返回模块级默认（bookingEnabled + 默认 walkInSlotMode）。 */
    private Long organizationId;
}

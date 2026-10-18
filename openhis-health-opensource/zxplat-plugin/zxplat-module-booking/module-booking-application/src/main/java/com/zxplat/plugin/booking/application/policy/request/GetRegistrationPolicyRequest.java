/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加载院点挂号策略请求。
 */
@Getter
@Setter
public class GetRegistrationPolicyRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;
}

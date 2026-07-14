/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加载停诊变更策略请求（2.20）。
 */
@Getter
@Setter
public class GetScheduleChangePolicyRequest {

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;
}

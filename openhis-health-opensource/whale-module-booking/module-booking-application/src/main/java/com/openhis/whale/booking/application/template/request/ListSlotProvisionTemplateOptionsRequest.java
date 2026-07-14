/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * T1 下拉选项请求。
 */
@Getter
@Setter
public class ListSlotProvisionTemplateOptionsRequest {

    /** 科室主键。 */
    private Long organizationId;
}

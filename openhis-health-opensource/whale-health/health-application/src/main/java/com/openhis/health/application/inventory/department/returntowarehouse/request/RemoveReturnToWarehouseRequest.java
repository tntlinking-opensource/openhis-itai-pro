/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除科室退库单请求。
 */
@Getter
@Setter
public class RemoveReturnToWarehouseRequest {

    /** 业务单号。 */
    private String busNo;
}

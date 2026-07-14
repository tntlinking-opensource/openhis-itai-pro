/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号查询科室退库单详情请求。
 */
@Getter
@Setter
public class GetReturnToWarehouseDetailRequest {

    /** 业务单号。 */
    private String busNo;
}

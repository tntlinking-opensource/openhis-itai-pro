/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 修订科室退库单成功响应。
 */
@Getter
@Setter
public class UpdateReturnToWarehouseResponse {

    /** 业务单号。 */
    private String busNo;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
}

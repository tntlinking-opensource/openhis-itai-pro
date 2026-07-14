/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除科室退库单成功响应。
 */
@Getter
@Setter
public class RemoveReturnToWarehouseResponse {

    /** 操作是否成功。 */
    private Boolean success;
}

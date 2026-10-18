/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回科室退库单成功响应。
 */
@Getter
@Setter
public class RejectReturnToWarehouseResponse {

    /** 操作是否成功。 */
    private Boolean success;
}

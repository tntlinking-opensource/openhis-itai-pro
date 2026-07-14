/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室退库页主数据初始化请求（机构由会话解析，字段可选）。
 */
@Getter
@Setter
public class LoadReturnToWarehouseFormInitRequest {

    /** 机构主键（可选；通常由服务端会话解析，前端可不传）。 */
    private Long organizationId;
}

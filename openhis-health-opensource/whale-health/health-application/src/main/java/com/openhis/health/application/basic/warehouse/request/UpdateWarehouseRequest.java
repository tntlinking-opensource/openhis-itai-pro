/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.warehouse.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑库房/药房请求（含主键，其余字段继承创建请求）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateWarehouseRequest extends CreateWarehouseRequest {
    /** 位置主键。 */
    @NotNull(message = "ID不能为空")
    private Long id;
}

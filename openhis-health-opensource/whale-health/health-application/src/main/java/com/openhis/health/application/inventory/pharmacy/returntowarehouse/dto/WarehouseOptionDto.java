/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 库位/药房/药库下拉选项行。
 *
 * <p>{@code locationId} 以字符串序列化，避免前端 Number 精度丢失导致 ElSelect 无法匹配回显。</p>
 */
@Getter
@Setter
public class WarehouseOptionDto {

    /** 库位主键（JSON 字符串，防 Snowflake 精度丢失）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;
    /** 展示名称。 */
    private String displayName;
    /** 是否启用。 */
    private Boolean active;
}

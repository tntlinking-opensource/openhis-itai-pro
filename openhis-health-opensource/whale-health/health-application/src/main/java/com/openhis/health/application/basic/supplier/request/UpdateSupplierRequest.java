/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.supplier.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 供应商编辑请求：在创建字段基础上增加主键与输入码。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "供应商")
public class UpdateSupplierRequest extends CreateSupplierRequest {
    /** ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "ID", example = "9876543210")
    private Long id;

    /** 拼音码。 */
    @Schema(description = "拼音码", example = "abc")
    private String pyStr;
    /** 五笔码。 */
    @Schema(description = "五笔码", example = "abc")
    private String wbStr;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stockin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室入库页初始化请求（IF-DEPT-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室入库页初始化请求 IF-DEPT-IN-01")
public class LoadStockInFormInitRequest {

    /** 机构主键，兼容字段，服务端忽略。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键，兼容字段，服务端忽略")
    private Long organizationId;
}

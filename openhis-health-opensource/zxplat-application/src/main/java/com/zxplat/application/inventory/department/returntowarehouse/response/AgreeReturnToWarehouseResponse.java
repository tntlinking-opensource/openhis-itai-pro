/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室退库同意成功响应（含系统生成的退库入库壳单号）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeReturnToWarehouseResponse {

    /** 科室退库单号。 */
    @Schema(description = "科室退库单号")
    private String busNo;

    /** 退库单状态。 */
    @Schema(description = "退库单状态")
    private String statusEnum;

    /** 系统生成的退库入库壳单号。 */
    @Schema(description = "系统生成的退库入库壳单号")
    private String generatedReturnInboundBusNo;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}

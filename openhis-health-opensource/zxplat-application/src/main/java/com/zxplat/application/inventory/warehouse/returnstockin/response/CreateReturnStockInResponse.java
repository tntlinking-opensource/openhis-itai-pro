/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建退库入库壳单成功响应（IF-WHS-RSI-04，接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "新建退库入库壳单 IF-WHS-RSI-04（已拒绝）")
public class CreateReturnStockInResponse {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
}

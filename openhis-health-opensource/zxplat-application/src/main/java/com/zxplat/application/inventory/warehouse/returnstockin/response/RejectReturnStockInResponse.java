/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 驳回退库入库壳单成功响应（IF-WHS-RSI-09，接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "驳回退库入库 IF-WHS-RSI-09（已拒绝）")
public class RejectReturnStockInResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}

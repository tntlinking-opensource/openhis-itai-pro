/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除退库入库壳单请求（IF-WHS-RSI-10，须保留追溯链：接口固定拒绝 WHS_RSI_006）。
 */
@Getter
@Setter
@Schema(description = "删除退库入库壳单 IF-WHS-RSI-10（已拒绝）")
public class RemoveReturnStockInRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}

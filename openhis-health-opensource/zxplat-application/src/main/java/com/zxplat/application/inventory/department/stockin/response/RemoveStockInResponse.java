/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 删除科室入库单响应（IF-DEPT-IN-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除科室入库单 IF-DEPT-IN-10")
public class RemoveStockInResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}

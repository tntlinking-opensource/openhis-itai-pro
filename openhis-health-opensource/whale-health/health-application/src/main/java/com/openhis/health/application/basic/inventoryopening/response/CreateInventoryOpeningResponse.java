/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 新建/复制期初单成功响应（IF-BAS-INV-OPN-04/16）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建期初响应")
public class CreateInventoryOpeningResponse {

    /** 新建业务单号。 */
    @Schema(description = "单号")
    private String busNo;
}

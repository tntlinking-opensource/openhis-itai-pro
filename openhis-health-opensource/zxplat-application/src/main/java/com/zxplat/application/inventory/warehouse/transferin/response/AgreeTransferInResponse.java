/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批同意药库调入响应（IF-WHS-TI-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意药库调入 IF-WHS-TI-08")
public class AgreeTransferInResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}

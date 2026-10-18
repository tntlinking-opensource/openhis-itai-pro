/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批同意药房调入响应（IF-PHA-TI-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意药房调入 IF-PHA-TI-08")
public class AgreeTransferInResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 同意后状态 SupplyStatus#getCode。 */
    @Schema(description = "同意后状态 SupplyStatus#getCode")
    private String statusEnum;
}

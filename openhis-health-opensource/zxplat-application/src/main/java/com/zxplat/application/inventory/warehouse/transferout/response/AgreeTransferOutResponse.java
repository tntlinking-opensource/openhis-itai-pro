/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferout.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 调出业务单号。 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeTransferOutResponse {
    /** 调出单号。 */
    @Schema(description = "调出单号")
    private String busNo;

    /** 调出单状态。 */
    @Schema(description = "调出单状态")
    private String statusEnum;

    /** 系统生成的调入壳单号。 */
    @Schema(description = "系统生成的调入壳单号")
    private String generatedTransferInBusNo;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 新建科室调入单响应（IF-DEPT-TI-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "新建科室调入单 IF-DEPT-TI-04")
public class CreateTransferInResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
}

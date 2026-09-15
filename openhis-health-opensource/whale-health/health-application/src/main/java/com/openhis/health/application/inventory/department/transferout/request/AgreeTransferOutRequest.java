/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 同意科室调出单请求（IF-DEPT-TR-08）。
 *
 * <p>触发同意链：领域同意、发放、源库扣减、目的入账、对侧调入壳单与追溯写入由应用服务编排，单事务边界见 AS-SDD。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeTransferOutRequest {

    /** 调出业务单号。 */
    @Schema(description = "调出业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}

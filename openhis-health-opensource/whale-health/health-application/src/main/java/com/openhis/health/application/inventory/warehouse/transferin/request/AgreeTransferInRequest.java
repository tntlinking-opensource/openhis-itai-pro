/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.transferin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批同意药库调入（手工单）请求（IF-WHS-TI-08）。
 *
 * <p>本服务为只读台账侧，接口统一拒绝手工同意；请求体保留契约兼容。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "审批同意药库调入 IF-WHS-TI-08", requiredProperties = {"busNo"})
public class AgreeTransferInRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药房请领单同意请求（IF-PHA-QL-08）。
 *
 * <p>同意仅调用领域 {@code agreeRequest} 置供应请求为已同意（BR-PHA-QL-009），
 * <strong>不</strong>触发库存变动、发放或药库出库/药房入库执行单生成。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.pharmacy.requisition.service.InventoryPharmacyRequisitionAppService#agreePharmacyRequisition(AgreePharmacyRequisitionRequest)}。</p>
 */
@Getter
@Setter
@Schema(description = "药房请领单同意 IF-PHA-QL-08", requiredProperties = {"busNo"})
public class AgreePharmacyRequisitionRequest {

    /** 请领业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "请领业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 审批人主键；可空，空时由应用层回落为当前操作人（单岗工作站可不传）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键，可空时取当前登录用户")
    /** 审批人主键，可空时取当前登录用户。 */
    private Long approverId;

    /**
     * 审批业务时钟；可空时由领域或命令默认值填充，用于审计与状态迁移时间线。
     */
    @Schema(description = "审批业务时钟，可空")
    private LocalDateTime approvalClock;
}

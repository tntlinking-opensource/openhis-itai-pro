/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 药房请领单同意请求（IF-PHA-QL-08）。
 *
 * <p>同意仅调用领域 {@code agreeRequest} 置供应请求为已同意（BR-PHA-QL-009），
 * <strong>不</strong>触发库存变动、发放或药库出库/药房入库执行单生成。
 * 审批人与审批时刻由应用层取当前操作人、由领域写 {@code approval_time}，请求体不再接收审批人/时钟字段。</p>
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
}

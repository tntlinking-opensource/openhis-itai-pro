/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 住院确认退药请求（IF-IPR-04）。
 */
@Data
/** 住院确认退药请求。 */
@Schema(description = "住院确认退药请求", requiredProperties = {"items"})
public class ConfirmInpatientMedicineReturnRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待确认退药发放行（须 {@code COMPLETED} 且退药数量/批号与明细一致）。 */
    @Schema(description = "待确认退药发放行列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请选择要退药的药品")
    @Valid
    private List<InpatientReturnMedicineItemRequest> items;

    /** 退药说明，写入 support_info。 */
    @Schema(description = "退药说明，写入 support_info")
    private String reason;
}

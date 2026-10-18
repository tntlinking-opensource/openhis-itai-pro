/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 确认或拒绝退药请求（IF-PHA-02-04）。
 */
@Data
/** 确认或拒绝退药请求。 */
@Schema(description = "确认或拒绝退药请求")
public class ConfirmMedicineReturnRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "退药项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReturnMedicineItemRequest> items;

    /** 操作类型（CONFIRM / DECLINE）。 */
    @Schema(description = "操作类型（CONFIRM / DECLINE）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmType;

    /** 退药/拒绝原因；拒绝时必填。 */
    @Schema(description = "退药/拒绝原因；拒绝时必填")
    private String reason;
}

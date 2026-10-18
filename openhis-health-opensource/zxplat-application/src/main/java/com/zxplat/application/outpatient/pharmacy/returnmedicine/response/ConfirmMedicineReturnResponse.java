/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.response;

import com.zxplat.application.outpatient.pharmacy.returnmedicine.dto.FailedReturnItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 确认或拒绝退药响应（IF-PHA-02-04）。
 */
@Data
/** 确认或拒绝退药响应。 */
@Schema(description = "确认或拒绝退药响应")
public class ConfirmMedicineReturnResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean success;

    /** 提示信息。 */
    @Schema(description = "提示信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    /** 失败项列表。 */
    @Schema(description = "失败项列表")
    private List<FailedReturnItemDto> failedItems;
}

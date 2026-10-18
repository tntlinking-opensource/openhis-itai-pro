/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.request;

import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.CreateMedicationSummaryDispenseItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 汇总保存请求（IF-IP-SUM-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总保存 IF-IP-SUM-04", requiredProperties = {"dispenseItems"})
public class CreateMedicationSummaryRequest {

    /** 勾选发放行（1～500）。 */
    @NotEmpty(message = "请勾选至少一条待汇总明细")
    @Valid
    /** 勾选发放行。 */
    @Schema(description = "勾选发放行", required = true)
    private List<CreateMedicationSummaryDispenseItemDto> dispenseItems;
}

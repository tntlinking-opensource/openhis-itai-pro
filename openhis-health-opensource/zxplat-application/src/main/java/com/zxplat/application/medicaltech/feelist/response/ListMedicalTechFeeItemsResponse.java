/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.feelist.response;

import com.zxplat.application.medicaltech.feelist.dto.MedicalTechFeeItemRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 查询医技费用清单响应（AS-MT-FEE-001 IF-MT-FEE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询医技费用清单响应")
public class ListMedicalTechFeeItemsResponse {

    /** 本科相关费用行。 */
    @Schema(description = "费用行")
    private List<MedicalTechFeeItemRowDto> rows;

    /** 行金额合计（MonetaryAmountSupport 汇总）。 */
    @Schema(description = "合计金额")
    private BigDecimal sumAmount;
}

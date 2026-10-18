/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.response;

import com.zxplat.application.inpatient.nursestation.ATDManager.dto.DischargePendingMedicationItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-04：出院前待发/待退药列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待发退药列表响应")
public class ListDischargePendingMedicationsResponse {

    @Schema(description = "待发退药行列表")
    /** 待发退药行列表。 */
    private List<DischargePendingMedicationItemDto> items;
}

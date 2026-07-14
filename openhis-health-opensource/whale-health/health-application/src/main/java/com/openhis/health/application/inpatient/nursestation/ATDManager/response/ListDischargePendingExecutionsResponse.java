/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.response;

import com.openhis.health.application.inpatient.nursestation.ATDManager.dto.DischargePendingExecutionItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-03：出院前待执行医嘱列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待执行医嘱列表响应")
public class ListDischargePendingExecutionsResponse {

    @Schema(description = "待执行医嘱行列表")
    /** 待执行医嘱行列表。 */
    private List<DischargePendingExecutionItemDto> items;
}

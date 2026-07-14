/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊护士站耗材房下拉响应：当前登录者管辖的启用态药房。
 */
@Getter
@Setter
public class ListManagedSupplyRoomOptionsResponse {

    @Schema(description = "耗材房下拉（登录者管辖药房）")
    /** 耗材房下拉（登录者管辖药房）。 */
    private List<OptionDto> supplyRoomOptions;
}

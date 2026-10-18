/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import com.zxplat.application.medicaltech.charging.dto.MedicalTechPricedDeviceRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询已划价耗材列表响应（AS-MT-CHG-001 IF-MT-CHG-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询已划价耗材列表响应")
public class ListMedicalTechPricedDevicesResponse {

    /** 已划价行。 */
    @Schema(description = "已划价行")
    private List<MedicalTechPricedDeviceRowDto> rows;
}

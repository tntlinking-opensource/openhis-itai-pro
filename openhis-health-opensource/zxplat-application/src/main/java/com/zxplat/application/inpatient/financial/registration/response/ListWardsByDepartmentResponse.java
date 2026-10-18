/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.zxplat.application.inpatient.financial.registration.dto.WardOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按科室查询病区列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区列表")
public class ListWardsByDepartmentResponse {

    /** 病区列表（业务字段）。 */
    @Schema(description = "病区列表")
    private List<WardOptionDto> wards;
}

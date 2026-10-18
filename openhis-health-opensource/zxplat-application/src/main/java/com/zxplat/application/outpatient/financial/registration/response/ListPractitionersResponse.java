/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医生列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生列表响应")
public class ListPractitionersResponse {

    /** 医生选项（业务字段）。 */
    @Schema(description = "医生选项")
    private List<OptionDto> practitionerOptions;
}

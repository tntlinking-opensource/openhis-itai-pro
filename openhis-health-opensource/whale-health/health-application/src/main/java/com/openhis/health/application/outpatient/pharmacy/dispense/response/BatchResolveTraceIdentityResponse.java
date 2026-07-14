/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.response;

import com.openhis.health.application.outpatient.pharmacy.dispense.dto.TraceIdentityResolveFailItemDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.TraceIdentityResolveItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量扫码身份反查响应（IF-TRC-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量扫码身份反查响应")
public class BatchResolveTraceIdentityResponse {

    /** 成功解析项（与入参同序子集）。 */
    @Schema(description = "成功解析项")
    private List<TraceIdentityResolveItemDto> items = new ArrayList<>();

    /** 失败项。 */
    @Schema(description = "失败项")
    private List<TraceIdentityResolveFailItemDto> failItems = new ArrayList<>();
}

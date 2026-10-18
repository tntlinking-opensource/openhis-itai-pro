/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt.response;

import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.TraceIdentityResolveFailItemDto;
import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.TraceIdentityResolveItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量扫码身份反查响应（IF-TRC-21）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量扫码身份反查响应 IF-TRC-21")
public class BatchResolveTraceIdentityResponse {

    /** 成功解析项。 */
    @Schema(description = "成功解析项")
    private List<TraceIdentityResolveItemDto> items;

    /** 失败项。 */
    @Schema(description = "失败项")
    private List<TraceIdentityResolveFailItemDto> failItems;
}

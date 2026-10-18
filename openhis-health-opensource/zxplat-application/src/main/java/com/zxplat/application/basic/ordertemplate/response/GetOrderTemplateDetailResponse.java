/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.response;

import com.zxplat.application.basic.ordertemplate.dto.OrderTemplateHeaderRowDto;
import com.zxplat.application.basic.ordertemplate.dto.OrderTemplateLineRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 组套模板详情响应（IF-BAS-OTL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板详情响应")
public class GetOrderTemplateDetailResponse {

    @Schema(description = "组套头")
    /** 明细行列表。 */
    private OrderTemplateHeaderRowDto header;

    @Schema(description = "明细行列表")
    private List<OrderTemplateLineRowDto> lines;
}

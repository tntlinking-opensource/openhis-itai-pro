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
 * IF-BAS-OTL-08：预览组套套用明细响应。
 *
 * <p>返回头行结构（定义 id、默认数量/频次/用法等），并回填目录展示名（{@code productName} 等）。
 * <strong>不含</strong>实时单价与 {@code defaultDispenseLocationId}。前端须以行内 id 调 IF-DOR-11 / IF-IP-PRC-01
 * {@code prepickedProductKeys} 补齐定价与库位（与 AS-BAS-MST-017 BR-OTL-028 一致）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "预览组套套用响应（含目录展示名；无价/无库位 enrich）")
public class PreviewOrderTemplateApplyResponse {

    @Schema(description = "组套头")
    /** 组套明细行。 */
    private OrderTemplateHeaderRowDto header;

    @Schema(description = "组套明细行")
    private List<OrderTemplateLineRowDto> lines;
}

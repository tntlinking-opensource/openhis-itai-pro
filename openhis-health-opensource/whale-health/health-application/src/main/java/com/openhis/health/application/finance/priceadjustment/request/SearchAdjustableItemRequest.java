/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 可选调价项目搜索请求（IF-PRC-04）。
 * <p>医疗服务 Tab 的操作科室由服务端从当前登录会话解析，不在请求体上传。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "可选调价项目搜索请求", requiredProperties = {"itemTable"})
public class SearchAdjustableItemRequest extends PageQueryBinder.AbstractHealthPageRequest {

    /** 项目物理表。 */
    @NotBlank(message = "项目表不能为空")
    @Schema(description = "项目物理表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;
}

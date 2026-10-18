/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 维护端组套模板分页查询请求（IF-BAS-OTL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "维护端组套模板分页查询请求", requiredProperties = {"templateKindEnum", "pageNo", "pageSize"})
public class PageMaintainedOrderTemplateRequest extends AbstractHealthPageRequest {

    @Schema(description = "组套种类 code", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 绑定类型 code。 */
    private String templateKindEnum;

    @Schema(description = "绑定类型 code")
    private String bindingEnum;

    /** 适用场景 code。 */
    @Schema(description = "适用场景 code")
    private String sceneEnum;

    /** 状态 code。 */
    @Schema(description = "状态 code")
    private String statusEnum;

    /**
     * 模板来源筛选（MAINTAINED / FROM_ADVICE）；缺省不过滤，维护页可展示医生站另存模板。
     */
    @Schema(description = "模板来源 code（MAINTAINED / FROM_ADVICE）；缺省不过滤")
    private String templateSourceEnum;

    /** 名称/编码关键字（与 keyword 二选一）。 */
    @Schema(description = "名称/编码关键字（与 keyword 二选一）")
    private String searchKey;
}

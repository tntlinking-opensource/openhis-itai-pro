/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.ordertemplate.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
    private String templateKindEnum;

    @Schema(description = "绑定类型 code")
    private String bindingEnum;

    /** 适用场景筛选。 */
    @Schema(description = "适用场景 code")
    private String sceneEnum;

    /** 状态筛选。 */
    @Schema(description = "状态 code")
    private String statusEnum;

    /**
     * 模板来源筛选（MAINTAINED / FROM_ADVICE）；缺省不过滤，维护页可展示医生站另存模板。
     */
    @Schema(description = "模板来源 code（MAINTAINED / FROM_ADVICE）；缺省不过滤")
    private String templateSourceEnum;

    /** 名称/编码关键字（SDD 字段名；与父类 keyword 二选一）。 */
    @Schema(description = "名称/编码关键字（与 keyword 二选一）")
    private String searchKey;
}

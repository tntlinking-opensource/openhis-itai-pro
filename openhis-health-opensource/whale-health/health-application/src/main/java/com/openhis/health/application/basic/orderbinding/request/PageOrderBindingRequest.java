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

package com.openhis.health.application.basic.orderbinding.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务设备模板绑定分页查询条件：按范围、绑定类型、来源项与状态过滤；分页见 {@link AbstractPageQuery}（B.10.11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageOrderBindingRequest extends AbstractHealthPageRequest {

    /** 绑定范围业务码；空串由应用服务默认全院范围。 */
    private String rangeCode;

    /** 绑定类型：1 号源 2 用法 3 诊疗，与 {@link com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode} 对应。 */
    private Integer bindType;

    /** 来源项主键（字符串形式，与领域 itemId 一致）；空表示不按来源过滤。 */
    private String sourceId;

    /** 启用状态筛选：1 启用 2 停用；空表示不按状态过滤。 */
    private Integer statusEnum;

    /**
     * 默认首頁、每页 50 条（与历史字段初值一致）。
     */
    public PageOrderBindingRequest() {
        setPageNo(1);
        setPageSize(50);
    }
}

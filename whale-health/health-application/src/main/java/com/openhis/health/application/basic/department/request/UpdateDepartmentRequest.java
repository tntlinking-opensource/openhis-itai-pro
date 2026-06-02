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

package com.openhis.health.application.basic.department.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑科室请求参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDepartmentRequest extends CreateDepartmentRequest {

    /** 科室主键。 */
    @NotNull(message = "ID不能为空")
    private Long id;

    /**
     * 上级组织是否发生变更（用于区分“未传 parentId”与“显式改为根节点”）。
     */
    private Boolean parentIdChanged;
}

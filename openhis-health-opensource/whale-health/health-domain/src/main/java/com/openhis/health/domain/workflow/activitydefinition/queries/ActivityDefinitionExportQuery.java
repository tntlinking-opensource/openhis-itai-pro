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

package com.openhis.health.domain.workflow.activitydefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗目录导出查询入参。
 *
 * <p>默认与列表筛选口径一致；具体导出列由 QueryModel 决定。</p>
 */
@Getter
@Setter
public class ActivityDefinitionExportQuery {
    /** 目录类别编码（可选）。 */
    private String categoryCode;

    /** 关键字（可选）。 */
    private String keyword;

    /** 发布状态（可选）。 */
    private String statusEnum;

    /** 诊疗类型（可选）。 */
    private String typeEnum;
}

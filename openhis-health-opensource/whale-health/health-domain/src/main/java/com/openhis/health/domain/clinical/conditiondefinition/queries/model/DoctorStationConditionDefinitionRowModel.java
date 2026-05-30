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

package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 医生站「新增诊断」弹窗分页行：诊断定义检索结果（读侧投影）。
 */
@Getter
@Setter
public class DoctorStationConditionDefinitionRowModel {
    /** 诊断定义主键。 */
    private Long id;
    /** 诊断编码。 */
    private String conditionCode;
    /** 诊断名称。 */
    private String name;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 目录来源分类编码（字符串持久化值）。 */
    private String sourceEnum;
    /** 发布状态编码。 */
    private String statusEnum;
    /** 医保目录标志。 */
    private Boolean ybFlag;
    /** 医保诊断编码（目录）。 */
    private String ybNo;
}

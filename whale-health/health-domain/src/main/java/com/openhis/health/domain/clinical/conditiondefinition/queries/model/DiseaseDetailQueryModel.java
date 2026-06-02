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
 * 诊断定义详情查询模型。
 */
@Getter
@Setter
public class DiseaseDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** 类型编码。 */
    private String sourceEnum;
    private String pyStr;
    private String wbStr;
    private String typeCode;
    /** description。 */
    private String description;
    /** 医保号。 */
    private String ybNo;
    private Boolean ybFlag;
    private String diseaseCode;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 状态枚举编码。 */
    private String statusEnum;
}

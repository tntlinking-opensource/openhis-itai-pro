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

package com.openhis.health.application.basic.servicecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 诊疗目录子项（组合项目中的子服务/子收费单元）在创建与更新请求中的行结构。
 */
@Data
public class ServiceCatalogChildDto {

    /** 子项行主键（编辑回填时非空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属父活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 子项引用主数据标识（如子诊疗/耗材定义主键）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long childrenId;

    /** 子项数量或次数。 */
    private Integer childrenNum;

    /** 子项展示名称。 */
    private String childrenName;
}

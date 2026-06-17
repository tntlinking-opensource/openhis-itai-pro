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

package com.openhis.health.domain.administration.specimendefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 样本定义分页行投影。
 */
@Getter
@Setter
public class SpecimenDefinitionPageItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务编码。 */
    private String busNo;
    /** 样本名称。 */
    private String name;
    /** 标本种类。 */
    private String typeEnum;
    /** 发布状态。 */
    private String statusEnum;
    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}

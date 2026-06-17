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

package com.openhis.health.application.basic.specimendefinition.request;

import lombok.Data;

/**
 * 新建样本定义请求体。
 */
@Data
public class CreateSpecimenDefinitionRequest {

    /** 业务编码（可空，由领域按 SPD 前缀自动生成）。 */
    private String busNo;

    /** 样本名称。 */
    private String name;

    /** 标本种类，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

    /** 拼音码（可空，空则按名称自动生成）。 */
    private String pyStr;

    /** 五笔码（可空，空则按名称自动生成）。 */
    private String wbStr;
}

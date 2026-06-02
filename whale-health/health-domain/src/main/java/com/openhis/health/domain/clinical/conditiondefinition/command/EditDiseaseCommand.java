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

package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑诊断目录命令。
 */
@Getter
@Setter
public class EditDiseaseCommand {
    /** 主键。 */
    private Long id;
    /** source枚举编码。 */
    private String sourceEnum;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** pystr。 */
    private String pyStr;
    /** wbstr。 */
    private String wbStr;
    /** 类型编码。 */
    private String typeCode;
    /** description。 */
    private String description;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** 医保号。 */
    private String ybNo;

    private String diseaseCode;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    private String statusEnum;
}


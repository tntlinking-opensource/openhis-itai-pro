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

package com.openhis.health.application.basic.body.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 身体部位 DTO（部位管理菜单出参）。
 *
 *
 */
@Data
public class BodyDto {

    /** 身体部位主键（字符串序列化适配前端）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务编号 */
    private String busNo;

    /** 名称 */
    private String name;

    /** 上级部位ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 状态枚举值 */
    private String statusEnum;

    /** 状态枚举文本 */
    private String statusEnumText;

    /** 拼音码 */
    private String pyStr;

    /** 五笔码 */
    private String wbStr;
}

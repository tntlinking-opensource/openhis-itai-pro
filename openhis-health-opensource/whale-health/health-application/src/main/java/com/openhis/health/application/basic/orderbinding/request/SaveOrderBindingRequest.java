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

import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存服务设备模板绑定请求：无 {@code id} 为新建，有 {@code id} 为更新。
 */
@Data
public class SaveOrderBindingRequest {

    /** 绑定记录主键；新建时为 {@code null}。 */
    private Long id;

    /** 绑定范围业务码。 */
    private String rangeCode;

    /** 绑定类型：1 号源 2 用法 3 诊疗。 */
    private Integer bindType;

    /** 来源项标识（与绑定类型对应的 item 表主键字符串）。 */
    private String sourceId;

    /** 目标类型扩展位（若前端使用）；装配以 targetTable/targetId 为准。 */
    private Integer targetType;

    /** 目标主键字符串，解析为 Long 写入领域。 */
    private String targetId;

    /** 目标表名，须为诊疗定义表或设备定义表常量之一。 */
    private String targetTable;

    /** 绑定数量，须大于 0。 */
    private BigDecimal quantity;

    /** 单位码（术语 unit）。 */
    private String unitCode;

    /** 启用状态：1 启用 2 停用。 */
    private Integer statusEnum;
}

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

package com.openhis.health.domain.custom.servicedevicetemplate.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 新增诊疗耗材绑定命令。
 */
@Getter
@Setter
public class CreateBindingCommand {
    /**
     * 绑定范围编码（如：全院/科室等；可空时由领域默认值兜底）。
     */
    private String rangeCode;
    /**
     * 绑定类型编码。
     */
    private String typeCode;
    /**
     * 被绑定对象表名。
     */
    private String itemTable;
    /**
     * 被绑定对象主键（字符串形态）。
     */
    private String itemId;
    /**
     * 被绑定的诊疗/耗材对象表名。
     */
    private String devActTable;
    /**
     * 被绑定的诊疗/耗材对象主键。
     */
    private Long devActId;
    /**
     * 数量（可空）。
     */
    private BigDecimal quantity;
    /**
     * 单位编码（可空）。
     */
    private String unitCode;
    /**
     * 状态枚举值（可空；为空时由领域默认 active）。
     */
    private String statusEnum;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}

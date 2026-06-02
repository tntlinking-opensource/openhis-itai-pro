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

package com.openhis.health.application.basic.registrationtype.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 挂号类型维护界面展示：底层为医疗服务主数据，附带销售价金额（元，来自收费定义行解析）。
 */
@Data
public class RegistrationTypeDto {
    /** 医疗服务主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 挂号类型名称。 */
    private String name;
    /** 医保目录编码或对照码。 */
    private String ybNo;
    /** 业务流水号/内部编码。 */
    private String busNo;
    /** 启用标志：1 启用，0 停用。 */
    private Integer activeFlag;
    /** 挂号费销售金额（元，两位小数），无定价行时为 {@code null}。 */
    private BigDecimal amount;
}

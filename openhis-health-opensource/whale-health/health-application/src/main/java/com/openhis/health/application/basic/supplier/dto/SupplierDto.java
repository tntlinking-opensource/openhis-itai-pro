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

/* (C)2023 */
package com.openhis.health.application.basic.supplier.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 供应商维护与列表展示用 DTO（含部分字典回填展示字段）。
 */
@Data
@Accessors(chain = true)
public class SupplierDto {

    /** 业务编号。 */
    private String busNo;

    /** 供应商名称。 */
    private String name;

    /** 供应商类型（整型展示，与领域持久化字符串对照由 Mapper 解析）。 */
    private Integer typeEnum;

    /** 类型展示文案（前端字典或二次查询回填）。 */
    private String typeEnum_enumText;

    /** 地址。 */
    private String address;

    /** 统一社会信用代码。 */
    private String creditCode;

    /** 法定代表人。 */
    private String legalRepresentative;

    /** 拼音码。 */
    private String pyStr;

    /** 联系电话。 */
    private String phone;

    /** 电子邮箱。 */
    private String email;

    /** 状态（整型展示）。 */
    private Integer statusEnum;

    /** 状态展示文案。 */
    private String statusEnum_enumText;

    /** 所属机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 机构名称展示文案。 */
    private String orgId_dictText;

    /** 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 五笔码。 */
    private String wbStr;

    /** 开户银行。 */
    private String bankName;

    /** 银行账号。 */
    private String bankAccount;

    /** 备注。 */
    private String note;
}

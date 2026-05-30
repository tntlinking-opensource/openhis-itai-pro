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

/* (C)2026 */
package com.openhis.health.application.basic.supplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 供应商创建指令DTO
 *
 * @date 2025-12-30
 */
@Data
@Accessors(chain = true)
@Schema(description = "供应商创建指令DTO")
public class CreateSupplierRequest {

    /**
     * 供应商编号（可选；不传则后端按序列生成）
     */
    @Schema(description = "供应商编号（可选，不传则自动生成）", example = "SP00000001")
    private String busNo;

    /**
     * 供应商名称
     */
    @NotBlank(message = "供应商名称不能为空")
    @Schema(description = "供应商名称", required = true, example = "北京健康科技有限公司")
    private String name;

    /**
     * 供应商类型
     */
    @Schema(
            description = "供应商类型",
            example = "1",
            allowableValues = {"1", "2", "3"})
    private String typeEnum;

    /**
     * 统一社会信用代码
     */
    @Schema(description = "统一社会信用代码", required = true, example = "91110108MA01XXXXXX")
    private String creditCode;

    /**
     * 法定代表人
     */
    @Schema(description = "法定代表人", example = "张三")
    private String legalRepresentative;

    /**
     * 地址
     */
    @Schema(description = "供应商地址", example = "北京市海淀区中关村大街1号")
    private String address;


    /**
     * 联系电话
     */
    @Schema(description = "联系电话", example = "010-12345678")
    private String phone;

    /**
     * 电子邮箱
     */
    @Schema(description = "电子邮箱", example = "contact@bjhealthtech.com")
    private String email;

    @Schema(description = "开户银行", example = "中国工商银行")
    private String bankName;

    @Schema(description = "银行账号", example = "622202xxxxxxxxxxxx")
    private String bankAccount;


    /**
     * 所属机构ID
     */
    @Schema(description = "所属机构ID", example = "10001")
    private Long orgId;

    @Schema(description = "备注", example = "长期合作")
    private String note;
}

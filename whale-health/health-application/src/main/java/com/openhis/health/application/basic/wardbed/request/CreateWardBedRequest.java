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

package com.openhis.health.application.basic.wardbed.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新建病区/病房/床位位置请求。
 *
 * <p>枚举字段一律传 {@code code} 字符串；登记时领域默认 {@code ACTIVE} + {@code NOT_IN_USE}（由 {@code registerAsActive} 驱动）。</p>
 */
@Data
@Schema(description = "新建病区/病房/床位请求")
public class CreateWardBedRequest {

    @Schema(description = "业务编号（选填，一般由领域按父 busNo 级联生成）", example = "LO0010.LO0026")
    private String busNo;

    @Schema(description = "位置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "内科一楼病房")
    @NotBlank(message = "位置名称不能为空")
    private String name;

    @Schema(description = "状态 code（选填；登记即启用时由领域写入 ACTIVE）", example = "ACTIVE")
    private String statusEnum;

    @Schema(description = "运营状态 code（选填；领域登记默认 NOT_IN_USE）", example = "NOT_IN_USE")
    private String operationalEnum;

    @Schema(description = "模式 code（选填，默认 INSTANCE）", example = "INSTANCE")
    private String modeEnum;

    @Schema(description = "类型编码（选填）", example = "WARD-001")
    private String typeCode;

    @Schema(description = "类型扩展 JSON（选填，默认 {}）", example = "{}")
    private String typeJson;

    @Schema(description = "拼音简码（选填）", example = "NKYLF")
    private String pyStr;

    @Schema(description = "五笔简码（选填）", example = "MGSY")
    private String wbStr;

    @Schema(description = "物理形式 code：WARD / HOUSE / BED", requiredMode = Schema.RequiredMode.REQUIRED, example = "WARD")
    @NotBlank(message = "物理形式不能为空")
    private String formEnum;

    @Schema(description = "机构 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000000000000000001")
    @NotNull(message = "机构不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    @Schema(description = "显示顺序（选填）", example = "10")
    private Integer displayOrder;

    @Schema(description = "父级位置 ID（选填；0 表示顶级）", example = "0")
    private Long parentId;
}

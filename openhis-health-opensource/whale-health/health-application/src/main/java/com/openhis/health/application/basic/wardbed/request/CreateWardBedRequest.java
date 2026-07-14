/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 业务编号（选填，一般由领域按父 busNo 级联生成）。 */
    private String busNo;

    @Schema(description = "位置名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "内科一楼病房")
    @NotBlank(message = "位置名称不能为空")
    private String name;

    /** 状态 code（选填；登记即启用时由领域写入 ACTIVE）。 */
    @Schema(description = "状态 code（选填；登记即启用时由领域写入 ACTIVE）", example = "ACTIVE")
    private String statusEnum;

    /** 运营状态 code（选填；领域登记默认 NOT_IN_USE）。 */
    @Schema(description = "运营状态 code（选填；领域登记默认 NOT_IN_USE）", example = "NOT_IN_USE")
    private String operationalEnum;

    /** 模式 code（选填，默认 INSTANCE）。 */
    @Schema(description = "模式 code（选填，默认 INSTANCE）", example = "INSTANCE")
    private String modeEnum;

    /** 类型编码（选填）。 */
    @Schema(description = "类型编码（选填）", example = "WARD-001")
    private String typeCode;

    /** 类型扩展 JSON（选填，默认 {}）。 */
    @Schema(description = "类型扩展 JSON（选填，默认 {}）", example = "{}")
    private String typeJson;

    /** 拼音简码（选填）。 */
    @Schema(description = "拼音简码（选填）", example = "NKYLF")
    private String pyStr;

    /** 五笔简码（选填）。 */
    @Schema(description = "五笔简码（选填）", example = "MGSY")
    private String wbStr;

    /** 物理形式 code：WARD / HOUSE / BED。 */
    @Schema(description = "物理形式 code：WARD / HOUSE / BED", requiredMode = Schema.RequiredMode.REQUIRED, example = "WARD")
    @NotBlank(message = "物理形式不能为空")
    private String formEnum;

    /** 机构 ID。 */
    @Schema(description = "机构 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000000000000000001")
    @NotNull(message = "机构不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 显示顺序（选填）。 */
    @Schema(description = "显示顺序（选填）", example = "10")
    private Integer displayOrder;

    /** 父级位置 ID（选填；0 表示顶级）。 */
    @Schema(description = "父级位置 ID（选填；0 表示顶级）", example = "0")
    private Long parentId;
}

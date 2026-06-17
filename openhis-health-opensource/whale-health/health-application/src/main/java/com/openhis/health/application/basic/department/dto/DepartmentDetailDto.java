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

package com.openhis.health.application.basic.department.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 科室详情 DTO：含医保扩展字段与可选嵌套的上级科室快照。
 */
@Data
public class DepartmentDetailDto {

    @Schema(description = "ID（以字符串形式序列化）", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "科室名称", example = "内科", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 机构类型枚举值。 */
    @Schema(description = "机构类型枚举值", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer typeEnum;
    /** 机构类型显示文本。 */
    @Schema(description = "机构类型显示文本", accessMode = Schema.AccessMode.READ_ONLY)
    private String typeEnum_dictText;

    /** 机构分类枚举值。 */
    @Schema(description = "机构分类枚举值", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer classEnum;
    /** 机构分类显示文本。 */
    @Schema(description = "机构分类显示文本", accessMode = Schema.AccessMode.READ_ONLY)
    private String classEnum_dictText;

    /** 医保机构编码。 */
    @Schema(description = "医保机构编码", example = "YB110101")
    private String ybNo;

    /** 医保机构名称。 */
    @Schema(description = "医保机构名称", example = "北京协和医院(医保)")
    private String ybName;

    /** 医保医药机构标识。 */
    @Schema(description = "医保医药机构标识", example = "H11000001")
    private String medinsId;

    /** 医保行政区划。 */
    @Schema(description = "医保行政区划", example = "110000")
    private String medinsAdmdvs;

    /** 医保医药机构类型。 */
    @Schema(description = "医保医药机构类型", example = "HOSPITAL")
    private String medinsType;

    /** 医保医药机构等级。 */
    @Schema(description = "医保医药机构等级", example = "三级甲等")
    private String medinsLv;

    /** 活动标识。 */
    @Schema(description = "活动标识 (1-启用, 2-停用)", example = "1")
    private Integer activeFlag;
    /** 活动标识显示文本。 */
    @Schema(description = "活动标识显示文本", accessMode = Schema.AccessMode.READ_ONLY)
    private String activeFlag_dictText;

    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** 默认医师 ID。 */
    private Long defDoctorId;
    /** 科别。 */
    private String caty;
    /** 科室业务编号。 */
    private String busNo;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 电子票据科室编码。 */
    private String eleInvoiceDeptCode;

    /** 上级科室详情（存在父级时填充）。 */
    private DepartmentDetailDto parentDepartment;
}

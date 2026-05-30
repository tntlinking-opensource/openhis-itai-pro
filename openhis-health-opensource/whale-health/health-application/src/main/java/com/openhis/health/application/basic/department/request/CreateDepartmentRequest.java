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

package com.openhis.health.application.basic.department.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增科室请求参数。
 */
@Data
public class CreateDepartmentRequest {

    /** 科室名称。 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 255)
    private String name;

    /** 科室业务编号。 */
    @Size(max = 255)
    private String busNo;

    /** 医保机构编码。 */
    @Size(max = 255)
    private String ybNo;

    /** 医保机构名称。 */
    @Size(max = 255)
    private String ybName;

    /** 医保医药机构标识。 */
    @Size(max = 255)
    private String medinsId;

    /** 医保行政区划。 */
    @Size(max = 255)
    private String medinsAdmdvs;

    /** 医保医药机构类型。 */
    @Size(max = 255)
    private String medinsType;

    /** 医保医药机构等级。 */
    @Size(max = 255)
    private String medinsLv;

    /** 拼音码。 */
    @Size(max = 255)
    private String pyStr;

    /** 五笔码。 */
    @Size(max = 255)
    private String wbStr;

    /** 默认医师 ID。 */
    private Long defDoctorId;
    /** 科别。 */
    private String caty;
    /** 科室分类（旧版枚举排序值）。 */
    private Integer classEnum;
    /** 科室类型编码。 */
    private String typeEnum;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 上级科室 ID。 */
    private Long parentId;

    /** 电子票据科室编码。 */
    @Size(max = 255)
    private String eleInvoiceDeptCode;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.department.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 科室列表/树节点 DTO。
 *
 * <p>同时承载平铺字段与树结构字段（children），用于列表页树表展示。</p>
 */
@Data
public class DepartmentDto {
    /** 科室主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 科室业务编号。 */
    private String busNo;
    /** 科室名称。 */
    private String name;
    /** 状态编码（与库 status_enum 一致，如 ACTIVE、INACTIVE）。 */
    private String statusEnum;
    /** 状态展示文案。 */
    private String statusEnum_dictText;
    /** 类型枚举排序值（旧版页面兼容）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer typeEnum;
    /** 类型展示文案。 */
    private String typeEnum_dictText;
    /** 分类枚举排序值（旧版页面兼容）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer classEnum;
    /** 分类展示文案。 */
    private String classEnum_dictText;
    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** 默认医师 ID。 */
    private Long defDoctorId;
    /** 医保机构编码。 */
    private String ybNo;
    /** 医保机构名称。 */
    private String ybName;
    /** 医保医药机构标识。 */
    private String medinsId;
    /** 医保行政区划。 */
    private String medinsAdmdvs;
    /** 医保医药机构类型。 */
    private String medinsType;
    /** 医保医药机构等级。 */
    private String medinsLv;
    /** 科别。 */
    private String caty;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 上级科室 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    /** 组织单元 ID（identity 侧）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ouId;
    /** 电子票据科室编码。 */
    private String eleInvoiceDeptCode;
    /** 子科室节点。 */
    private List<DepartmentDto> children;
}

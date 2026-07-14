/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicedevicebinding.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 诊疗耗材绑定列表行：来源项与目标（诊疗/耗材）及数量、单位、状态与展示 enrich 字段。
 */
@Data
public class ServiceDeviceBindingDto {

    /** 绑定记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 绑定范围业务码（如全院 HOSPITAL）。 */
    private String rangeCode;

    /** 绑定类型码：USAGE / ACTIVITY。 */
    private String typeCode;

    /** 绑定类型展示文案（enrich）。 */
    private String typeName;

    /** 来源项物理表名。 */
    private String itemTable;

    /** 来源项主键字符串；对外字段名保持 itemNo，与领域 itemId 一致。 */
    private String itemNo;

    /** 来源项展示名（enrich）。 */
    private String itemName;

    /** 目标物理表名（wkf_activity_definition / adm_device_definition）。 */
    private String devActTable;

    /** 目标表展示文案（enrich）。 */
    private String devActTableName;

    /** 目标主键（诊疗定义 id 或耗材定义 id）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long devActId;

    /** 目标项展示名（enrich）。 */
    private String devActName;

    /** 绑定数量，须为正数。 */
    private BigDecimal quantity;

    /** 单位码（术语 unit）。 */
    private String unitCode;

    /** 单位展示文案（enrich）。 */
    private String unitName;

    /** 发布状态码：ACTIVE / INACTIVE。 */
    private String statusEnum;

    /** 发布状态展示文案（enrich）。 */
    private String statusName;
}

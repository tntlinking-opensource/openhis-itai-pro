/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 检验设备对外展示 DTO（AS-BAS-MST-020）。
 */
@Data
public class LabDeviceDto {

    /** 主键 {@code adm_device.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 业务编码（LAD 前缀）。 */
    private String busNo;

    /** 设备名称。 */
    private String name;

    /** 拼音检索码。 */
    private String pyStr;

    /** 五笔检索码。 */
    private String wbStr;

    /** 发布状态 code（{@link com.openhis.health.domain.share.enums.PublicationStatus}）。 */
    private String statusEnum;

    /** 发布状态展示文案。 */
    private String statusEnumText;

    /** 设备分类 code（术语 adm_device_category_code）。 */
    private String categoryCode;

    /** 设备分类展示文案。 */
    private String categoryCodeText;

    /** 归属科室 {@code adm_organization.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 归属科室名称。 */
    private String orgName;

    /** 安装位置 {@code adm_location.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 安装位置名称。 */
    private String locationName;

    /** 型号。 */
    private String modelNumber;

    /** 序列号。 */
    private String serialNumber;

    /** 生产厂家 {@code cus_supplier.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long manufacturerId;

    /** 生产厂家名称。 */
    private String manufacturerName;

    /** 生产日期。 */
    private LocalDate manufactureDate;

    /** 失效/报废日期。 */
    private LocalDate expirationDate;

    /** 备注说明。 */
    private String description;

    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}

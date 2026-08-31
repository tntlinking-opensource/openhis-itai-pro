/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 维护检验设备请求体。
 */
@Data
public class UpdateLabDeviceRequest {

    /** 主键 {@code adm_device.id}。 */
    private Long id;

    /** 设备名称，必填。 */
    private String name;

    /** 拼音码。 */
    private String pyStr;

    /** 五笔码。 */
    private String wbStr;

    /** 归属科室 {@code adm_organization.id}。 */
    private Long orgId;

    /** 安装位置 {@code adm_location.id}。 */
    private Long locationId;

    /** 设备分类 code。 */
    private String categoryCode;

    /** 型号。 */
    private String modelNumber;

    /** 序列号。 */
    private String serialNumber;

    /** 生产厂家 {@code cus_supplier.id}。 */
    private Long manufacturerId;

    /** 生产日期。 */
    private LocalDate manufactureDate;

    /** 失效/报废日期。 */
    private LocalDate expirationDate;

    /** 备注说明。 */
    private String description;
}

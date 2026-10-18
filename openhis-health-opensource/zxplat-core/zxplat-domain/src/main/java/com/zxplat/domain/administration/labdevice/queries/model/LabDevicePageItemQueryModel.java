/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.queries.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验设备分页行投影。
 */
@Getter
@Setter
public class LabDevicePageItemQueryModel {

    /** 主键。 */
    private Long id;

    /** 业务编码。 */
    private String busNo;

    /** 设备名称。 */
    private String name;

    /** 发布状态 code。 */
    private String statusEnum;

    /** 设备分类 code。 */
    private String categoryCode;

    /** 拼音码。 */
    private String pyStr;

    /** 五笔码。 */
    private String wbStr;

    /** 归属科室。 */
    private Long orgId;

    /** 安装位置。 */
    private Long locationId;

    /** 型号。 */
    private String modelNumber;

    /** 序列号。 */
    private String serialNumber;

    /** 生产厂家。 */
    private Long manufacturerId;

    /** 生产日期。 */
    private LocalDate manufactureDate;

    /** 失效日期。 */
    private LocalDate expirationDate;

    /** 备注。 */
    private String description;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

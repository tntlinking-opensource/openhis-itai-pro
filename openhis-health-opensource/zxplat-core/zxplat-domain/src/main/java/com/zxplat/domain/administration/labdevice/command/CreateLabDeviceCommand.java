/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.command;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建检验设备命令。
 */
@Getter
@Setter
public class CreateLabDeviceCommand {

    /** 设备名称。 */
    private String name;

    /** 拼音码（可空，空则按名称生成）。 */
    private String pyStr;

    /** 五笔码（可空，空则按名称生成）。 */
    private String wbStr;

    /** 归属科室主键。 */
    private Long orgId;

    /** 物理位置主键。 */
    private Long locationId;

    /** 设备分类（术语 adm_device_category_code）。 */
    private String categoryCode;

    /** 型号。 */
    private String modelNumber;

    /** 序列号。 */
    private String serialNumber;

    /** 生产厂家（cus_supplier.id）。 */
    private Long manufacturerId;

    /** 生产日期。 */
    private LocalDate manufactureDate;

    /** 失效日期。 */
    private LocalDate expirationDate;

    /** 备注说明。 */
    private String description;
}

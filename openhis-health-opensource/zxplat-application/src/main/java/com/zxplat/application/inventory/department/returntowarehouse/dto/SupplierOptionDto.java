/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应商下拉选项行。
 */
@Getter
@Setter
public class SupplierOptionDto {

    /** 供应商主键。 */
    private Long supplierId;
    /** 供应商名称。 */
    private String supplierName;
    /** 供应商类型展示。 */
    private String supplierType;
}

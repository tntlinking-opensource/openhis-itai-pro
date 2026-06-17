/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.dto;

import lombok.Getter;
import lombok.Setter;

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

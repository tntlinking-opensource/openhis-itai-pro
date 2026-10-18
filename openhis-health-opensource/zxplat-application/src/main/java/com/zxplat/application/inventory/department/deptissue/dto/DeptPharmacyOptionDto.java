/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室药房下拉选项（IF-DEP-IS-01）。
 *
 * <p>在标准 OptionDto {@code value}/{@code label} 基础上扩展上级科室只读字段，供领用科室展示。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室药房下拉选项（含上级科室）")
public class DeptPharmacyOptionDto {

    /** 科室药房 locationId value。 */
    @Schema(description = "科室药房 locationId value")
    private String value;

    /** 科室药房名 label。 */
    @Schema(description = "科室药房名 label")
    private String label;

    /** 上级科室 ID organizationId。 */
    @Schema(description = "上级科室 ID organizationId")
    private String organizationId;

    /** 上级科室名 organizationName。 */
    @Schema(description = "上级科室名 organizationName")
    private String organizationName;
}

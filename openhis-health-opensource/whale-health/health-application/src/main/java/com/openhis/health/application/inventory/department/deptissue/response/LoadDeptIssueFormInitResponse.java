/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.response;

import com.openhis.health.application.inventory.department.deptissue.dto.DeptPharmacyOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室领用页初始化响应（IF-DEP-IS-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用页初始化 IF-DEP-IS-01")
public class LoadDeptIssueFormInitResponse {

    /** 科室药房下拉 departmentPharmacyOptions。 */
    @Schema(description = "科室药房下拉 departmentPharmacyOptions")
    private List<DeptPharmacyOptionDto> departmentPharmacyOptions;

    /** 项目类型下拉 productTableOptions。 */
    @Schema(description = "项目类型下拉 productTableOptions")
    private List<OptionDto> productTableOptions;

    /** 状态下拉 statusOptions。 */
    @Schema(description = "状态下拉 statusOptions")
    private List<OptionDto> statusOptions;

    /** 药师下拉 pharmacistOptions，可空。 */
    @Schema(description = "药师下拉 pharmacistOptions，可空")
    private List<OptionDto> pharmacistOptions;
}

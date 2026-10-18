/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室退领页初始化响应（IF-DEP-TL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领页初始化 IF-DEP-TL-01")
public class LoadDeptIssueReturnFormInitResponse {

    /** 科室药房选项 departmentPharmacies。 */
    @Schema(description = "科室药房选项 departmentPharmacies")
    private List<OptionDto> departmentPharmacies;

    /** 单据状态选项 supplyStatuses。 */
    @Schema(description = "单据状态选项 supplyStatuses")
    private List<OptionDto> supplyStatuses;

    /** 药师选项 pharmacists。 */
    @Schema(description = "药师选项 pharmacists")
    private List<OptionDto> pharmacists;
}

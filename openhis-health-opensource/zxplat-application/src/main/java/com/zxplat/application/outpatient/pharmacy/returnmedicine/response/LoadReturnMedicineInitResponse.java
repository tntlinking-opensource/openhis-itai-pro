/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.response;

import com.zxplat.application.outpatient.pharmacy.returnmedicine.dto.DepartmentOptionDto;
import com.zxplat.application.outpatient.pharmacy.returnmedicine.dto.RefundStatusOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 加载退药初始化元数据响应（IF-PHA-02-01）。
 */
@Data
/** 加载退药初始化元数据响应。 */
@Schema(description = "加载退药初始化元数据响应")
public class LoadReturnMedicineInitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "科室下拉选项")
    private List<DepartmentOptionDto> departmentOptions;

    /** 退药状态下拉选项。 */
    @Schema(description = "退药状态下拉选项")
    private List<RefundStatusOptionDto> refundStatusOptions;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 加载住院退药页初始化元数据响应（IF-IPR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载住院退药页初始化元数据响应")
public class LoadInpatientReturnMedicineInitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前用户管辖内启用药房下拉（value={@code adm_location.id}，队列/明细按库房收窄）。 */
    @Schema(description = "管辖内启用药房下拉；value 为 locationId")
    private List<OptionDto> pharmacyOrganizationOptions;

    /** 病区候选下拉；value 为 ward locationId。 */
    @Schema(description = "病区候选下拉；value 为 ward locationId")
    private List<OptionDto> wardOptions;

    /** 退药状态下拉：PENDING_REFUND / REFUNDED。 */
    @Schema(description = "退药状态下拉：PENDING_REFUND / REFUNDED")
    private List<OptionDto> refundStatusOptions;
}

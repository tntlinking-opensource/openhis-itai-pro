/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.supplier.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 供应商页初始化：类型下拉与机构下拉。
 */
@Data
@Accessors(chain = true)
@Schema(description = "供应商初始化数据传输对象")
public class SupplierInitDto {
    @Schema(description = "供应商类别选项列表")
    /** 所属机构选项列表（由组织树映射）。 */
    private List<OptionDto> supplierTypeOptions;

    @Schema(description = "所属机构选项列表（由组织树映射）")
    private List<OptionDto> organizationOptions;
}

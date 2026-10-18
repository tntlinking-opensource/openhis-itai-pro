/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.requisition.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室药房请领页初始化响应（IF-DEP-QL-01）。
 *
 * <p>返回目的科室药房（{@link com.zxplat.domain.share.enums.LocationForm#DEPARTMENT_PHARMACY}）、
 * 来源药库、入库项目类型、单据状态与人员下拉；位置选项经 {@code CurrentManagedLocationIdsResolver} 管辖收窄。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室药房请领页初始化 IF-DEP-QL-01")
public class LoadDepartmentRequisitionFormInitResponse {

    /** 目的科室药房下拉 pharmacyLocationOptions。 */
    @Schema(description = "目的科室药房下拉 pharmacyLocationOptions")
    private List<OptionDto> pharmacyLocationOptions;

    /** 来源药库下拉。 */
    @Schema(description = "来源药库下拉")
    private List<OptionDto> warehouseLocationOptions;

    /** 入库项目类型下拉（药品/耗材表名两档，与损益页同构）。 */
    @Schema(description = "入库项目类型 inboundItemTableTypeOptions")
    private List<OptionDto> inboundItemTableTypeOptions;

    /** 单据状态下拉 SupplyStatus。 */
    @Schema(description = "单据状态下拉 SupplyStatus")
    private List<OptionDto> statusOptions;

    /** 药师下拉（平台角色 {@code ROLE_PHARMACIST}，按会话机构收窄）。 */
    @Schema(description = "药师下拉 pharmacistOptions")
    private List<OptionDto> pharmacistOptions;

    /** 库管下拉（平台角色 {@code ROLE_WAREHOUSE}，按会话机构收窄）。 */
    @Schema(description = "库管下拉 warehouseKeeperOptions")
    private List<OptionDto> warehouseKeeperOptions;
}

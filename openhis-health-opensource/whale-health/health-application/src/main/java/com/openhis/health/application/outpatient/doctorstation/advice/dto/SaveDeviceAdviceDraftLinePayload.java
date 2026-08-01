/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-14：耗材医嘱草稿行载荷。
 *
 * <p>{@code dispenseLocationId} 为 IF-DOR-11 目录已解析库位；写路径经
 * {@code clientResolvedDispenseLocationId} FIFO 定价，不再二次 match 取药科室模板。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站耗材医嘱草稿行载荷")
public class SaveDeviceAdviceDraftLinePayload {

    /** {@code adm_device_definition} 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材定义主键")
    private Long deviceDefinitionId;

    /** 开立数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 默认执行科室（组织单元）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 执行科室。 */
    @Schema(description = "执行科室")
    private Long performOrgId;

    /**
     * 目录 IF-DOR-11 已解析发药库位；IF-DOR-14 写路径定价 FIFO 直接在该库位取批号，
     * 不再二次 match 取药科室模板。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库位（目录已解析，暂存定价用）")
    private Long dispenseLocationId;

    /**
     * 绑定就诊诊断主键有序串（落库 {@code wkf_device_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;
}

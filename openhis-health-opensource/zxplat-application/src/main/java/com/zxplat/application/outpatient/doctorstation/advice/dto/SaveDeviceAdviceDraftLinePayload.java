/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-14：耗材医嘱草稿行载荷。
 *
 * <p>医生站<strong>不</strong>选发材房：开立由服务端落取药科室模板默认配置到
 * {@code DeviceRequest.location_id}/{@code perform_location}。{@code dispenseLocationId} 仅供
 * 暂存费用草稿择价（目录回传）；换房在门诊护士站执行时指定，并与发放行保持一致。</p>
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

    /**
     * 执行科室（组织单元）；与发放耗材房无关，不得写入 {@code perform_location}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室")
    private Long performOrgId;

    /**
     * 目录 IF-DOR-11 回传的默认发材库位，仅用于暂存费用草稿择价；
     * <strong>不</strong>作为医生站选房写库；权威发材房由服务端默认配置落库，护士站可换房。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发材库位（仅择价；医生站不选房写库）")
    private Long dispenseLocationId;

    /**
     * 绑定就诊诊断主键有序串（落库 {@code wkf_device_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /**
     * 费用性质/合同覆盖，可空表示走患者默认；用于暂存挂账账户解析（与诊疗/药品同口径）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}

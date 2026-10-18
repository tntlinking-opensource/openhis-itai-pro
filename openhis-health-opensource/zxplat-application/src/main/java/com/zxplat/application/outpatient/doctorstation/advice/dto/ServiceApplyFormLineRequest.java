/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-21 / IF-IP-DOR-23：申请单主表一行（对应一条 {@code ServiceRequest} 草稿）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单主表一行")
public class ServiceApplyFormLineRequest {

    /** 诊疗活动定义主键。 */
    @NotNull(message = "诊疗定义主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activityDefinitionId;

    /** 数量。 */
    @NotNull(message = "数量不能为空")
    @Schema(description = "数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单位 code。 */
    @NotNull(message = "单位不能为空")
    @Schema(description = "单位 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /**
     * 本行执行次数（写入 {@code ServiceRequest.execution_count}）。
     * <p>优先于整单 {@code SaveDoctorStationServiceApplyFormRequest#executionCount}；空或非法时回落整单值，再回落 1。</p>
     */
    @Schema(description = "本行执行次数；优先于整单 executionCount")
    private Integer executionCount;

    /** 纯备注，落库 wkf_service_request.remark。 */
    @Schema(description = "纯备注，落库 wkf_service_request.remark")
    private String remark;

    /**
     * 医嘱优先级 code（DM-WKF-004；按项目加急为 {@code EM}；空表示非急）。
     */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;

    /** 部位/样本明细。 */
    @Valid
    @Schema(description = "部位/样本明细")
    private List<ServiceApplyFormDetailItemRequest> detailItems;
}

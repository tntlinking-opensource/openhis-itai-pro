/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量处置执行（诊疗/药品登记与耗材发放）请求（IF-NUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量处置执行请求")
public class BatchPerformDispositionRequest {

    /**
     * 就诊主键：批量执行必须限定在同一就诊，禁止跨就诊混批（AS §7）。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 操作/执行医务人员主键（{@code adm_practitioner.id}，与执行人登记同口径）。
     * 未传时由服务端经 {@code CurrentPractitionerResolver} 取当前登录绑定医务人员（B.6.5）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作/执行医务人员主键（可选；未传则取当前登录医务人员）")
    /** 操作/执行医务人员主键（可选；未传则取当前登录医务人员）。 */
    private Long operatorId;

    /**
     * 耗材房库位主键（BR-NUR-006 / IF-NUR-04）：护士指定时按该房强制 FIFO，回写发放行
     * {@code location_id}/批号与请求 {@code perform_location}；未传时回落医生签发已锁 {@code location_id}。
     * 院点可仅前端强制必选；后端不因缺省拒绝（缺库房且需 FIFO 时由编排门禁拒绝）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材房库位主键（传入则强制按该房 FIFO；未传回落发放单 location_id）")
    private Long inventoryLocationId;

    /**
     * 勾选的诊疗服务申请主键列表。
     */
    @Schema(description = "勾选的诊疗服务申请主键列表")
    private List<Long> serviceRequestIds;

    /**
     * 勾选的药品申请主键列表。
     */
    @Schema(description = "勾选的药品申请主键列表")
    private List<Long> medicationRequestIds;

    /**
     * 勾选的待发耗材发放行主键列表。
     */
    @Schema(description = "勾选的待发耗材发放行主键列表")
    private List<Long> deviceDispenseIds;
}

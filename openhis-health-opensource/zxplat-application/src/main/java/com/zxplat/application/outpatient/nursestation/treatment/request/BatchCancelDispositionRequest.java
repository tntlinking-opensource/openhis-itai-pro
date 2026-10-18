/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消执行（诊疗/药品登记抵消与耗材发放回退）请求（IF-NUR-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消执行请求")
public class BatchCancelDispositionRequest {

    /**
     * 就诊主键：批量取消必须限定在同一就诊，禁止跨就诊混批（AS §7）。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 操作医务人员主键（{@code adm_practitioner.id}）。
     * 未传时由服务端经 {@code CurrentPractitionerResolver} 取当前登录绑定医务人员（B.6.5）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作医务人员主键（可选；未传则取当前登录医务人员）")
    /** 操作医务人员主键（可选；未传则取当前登录医务人员）。 */
    private Long operatorId;

    /**
     * 取消原因：写入 {@code CancelProcedureRecordCommand#statusReasonText}（BR-PRO-005）。
     */
    @NotBlank(message = "statusReasonText 不能为空")
    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 取消原因。 */
    private String statusReasonText;

    /**
     * 勾选的净剩已完成执行记录主键列表（每条对应一次 CANCEL 抵消，{@code refund_id} 指向该 id）。
     */
    @Schema(description = "勾选的已完成执行记录主键列表（procedureId）")
    private List<Long> procedureIds;

    /**
     * 勾选的已发放耗材发放行主键列表（耗材取消语义独立，见 BR-NUR-012）。
     */
    @Schema(description = "勾选的已发放耗材发放行主键列表")
    private List<Long> deviceDispenseIds;
}

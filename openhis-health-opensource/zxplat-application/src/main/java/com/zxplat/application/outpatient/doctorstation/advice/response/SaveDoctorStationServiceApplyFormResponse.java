/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-21 / IF-IP-DOR-23：保存申请单响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存申请单响应")
public class SaveDoctorStationServiceApplyFormResponse {

    /**
     * 首张申请单号（AR 前缀；兼容旧客户端）。
     *
     * <p>按执行科室拆多张时仍返回第一张；完整列表见 {@link #prescriptionNos}。</p>
     */
    @Schema(description = "首张申请单号（AR 前缀）")
    private String prescriptionNo;

    /**
     * 本次生成的全部申请单号（按拆单组出现顺序）。
     *
     * <p>平台按执行科室拆多张；院点共看组（如 CT/DR）同组共一张。</p>
     */
    @Schema(description = "本次生成的全部申请单号")
    private List<String> prescriptionNos;

    /**
     * 本批新建 ServiceRequest 主键列表（与保存入参 lines 下标对齐）。
     *
     * <p>对齐后绑定耗材仍按行挂靠，不串单。</p>
     */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "本批新建 ServiceRequest 主键（与入参行对齐）")
    private List<Long> requestIds;

    /**
     * 每行所属申请单号（与 {@link #requestIds} / 入参 lines 下标对齐）。
     */
    @Schema(description = "每行所属申请单号（与 requestIds 对齐）")
    private List<String> requestPrescriptionNos;
}

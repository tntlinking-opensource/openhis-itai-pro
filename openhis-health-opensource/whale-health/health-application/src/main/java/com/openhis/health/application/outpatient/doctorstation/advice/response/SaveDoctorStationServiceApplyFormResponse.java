/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

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

    /** 新申请单号（AR 前缀）。 */
    @Schema(description = "新申请单号（AR 前缀）")
    private String prescriptionNo;

    /** 本单新建 ServiceRequest 主键列表。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "本单新建 ServiceRequest 主键列表")
    private List<Long> requestIds;
}

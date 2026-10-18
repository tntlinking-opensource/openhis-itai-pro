/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.response.SaveDoctorStationServiceApplyFormResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-23：住院保存申请单响应（与门诊 IF-DOR-21 同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院保存申请单响应")
public class SaveInpatientDoctorStationServiceApplyFormResponse extends SaveDoctorStationServiceApplyFormResponse {
}

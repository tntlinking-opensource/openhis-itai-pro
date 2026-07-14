/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationServiceApplyFormRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-23：住院保存检验/检查/治疗申请单请求（与门诊 IF-DOR-21 同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院保存检验/检查/治疗申请单请求")
public class SaveInpatientDoctorStationServiceApplyFormRequest extends SaveDoctorStationServiceApplyFormRequest {
}

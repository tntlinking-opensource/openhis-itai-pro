/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.response.ListDoctorStationServiceApplyFormsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-24：住院申请单列表响应（与门诊 IF-DOR-22 同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院申请单列表响应")
public class ListInpatientDoctorStationServiceApplyFormsResponse extends ListDoctorStationServiceApplyFormsResponse {
}

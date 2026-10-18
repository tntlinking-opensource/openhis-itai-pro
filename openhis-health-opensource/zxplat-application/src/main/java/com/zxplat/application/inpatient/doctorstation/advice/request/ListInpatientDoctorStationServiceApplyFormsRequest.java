/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.zxplat.application.outpatient.doctorstation.advice.request.ListDoctorStationServiceApplyFormsRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-24：住院查询申请单列表请求（与门诊 IF-DOR-22 同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院查询申请单列表请求")
public class ListInpatientDoctorStationServiceApplyFormsRequest extends ListDoctorStationServiceApplyFormsRequest {
}

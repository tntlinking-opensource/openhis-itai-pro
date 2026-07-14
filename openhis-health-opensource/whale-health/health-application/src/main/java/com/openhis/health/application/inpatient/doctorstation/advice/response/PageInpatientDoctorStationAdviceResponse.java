/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-DOR-22：住院医生站医嘱分页查询响应（全类型统一列表行，与 IF-IP-DOR-10 同构）。
 */
public class PageInpatientDoctorStationAdviceResponse extends PagedResult<InpatientDoctorStationAdviceRowDto> {
}

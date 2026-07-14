/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-DOR-20：门诊医生站医嘱分页查询响应（全类型统一列表行，与 IF-DOR-10 同构）。
 */
public class PageDoctorStationAdviceResponse extends PagedResult<DoctorStationAdviceRowDto> {
}

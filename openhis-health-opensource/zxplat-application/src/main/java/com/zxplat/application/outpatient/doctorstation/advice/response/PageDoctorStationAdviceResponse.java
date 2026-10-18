/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * IF-DOR-20：门诊医生站医嘱分页查询响应（全类型统一列表行，与 IF-DOR-10 同构）。
 */
public class PageDoctorStationAdviceResponse extends PagedResult<DoctorStationAdviceRowDto> {
}

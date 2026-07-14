/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-DOR-10：加载当前就诊医嘱统一列表响应。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code content} 为医嘱统一列表行数据。</p>
 */
public class ListInpatientDoctorStationAdviceResponse extends PagedResult<InpatientDoctorStationAdviceRowDto> {
}

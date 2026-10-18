/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * IF-DOR-10：加载当前就诊医嘱统一列表响应。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code content} 为医嘱统一列表行数据。</p>
 */
public class ListDoctorStationAdviceResponse extends PagedResult<DoctorStationAdviceRowDto> {
}

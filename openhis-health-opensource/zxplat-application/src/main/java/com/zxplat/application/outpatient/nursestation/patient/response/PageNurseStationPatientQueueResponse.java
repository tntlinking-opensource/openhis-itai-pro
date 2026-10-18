/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.patient.response;

import com.zxplat.application.outpatient.nursestation.patient.dto.NurseStationPatientQueueItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 门诊护士站患者队列分页响应（IF-NUR-01）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageNurseStationPatientQueueResponse extends PagedResult<NurseStationPatientQueueItemDto> {
}

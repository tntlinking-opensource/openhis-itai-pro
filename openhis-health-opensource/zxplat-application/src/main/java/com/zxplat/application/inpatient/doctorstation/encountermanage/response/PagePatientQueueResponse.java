/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.encountermanage.response;

import com.zxplat.application.inpatient.doctorstation.encountermanage.dto.DoctorStationPatientQueueItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 住院医生站在院患者列表分页响应（AS-IP-DOR-001 / IF-IP-DOR-01）。
 *
 * <p>继承 {@link com.zxplat.framework.core.application.dto.PagedResult}，对外字段为
 * {@code content}、{@code totalCount}、{@code pageNo}（1-based）、{@code pageSize}（B.10.11）；
 * 须经 {@link com.zxplat.application.shared.pagination.PagedResultBuilder} 装配。</p>
 */
public class PagePatientQueueResponse extends PagedResult<DoctorStationPatientQueueItemDto> {
}

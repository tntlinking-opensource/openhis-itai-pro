/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 分页检索可开立医嘱项目目录响应（IF-IP-DOR-11）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageInpatientDoctorStationAdviceCatalogResponse extends PagedResult<InpatientDoctorStationAdviceCatalogRowDto> {
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 分页检索可开立医嘱项目目录响应（IF-DOR-11）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageDoctorStationAdviceCatalogResponse extends PagedResult<DoctorStationAdviceCatalogRowDto> {
}

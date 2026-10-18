/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * IF-IP-TCM-35：中药饮片目录分页响应。
 *
 * <p>行契约与 {@code IF-IP-DOR-11} {@link InpatientDoctorStationAdviceCatalogRowDto} 同构，仅入口强制中草药类别收窄。</p>
 */
public class PageTcmHerbalMedicationCatalogResponse extends PagedResult<InpatientDoctorStationAdviceCatalogRowDto> {
}

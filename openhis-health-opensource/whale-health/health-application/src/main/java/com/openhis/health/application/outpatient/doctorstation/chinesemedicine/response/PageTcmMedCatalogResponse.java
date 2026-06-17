/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-DOR-35：分页检索中药饮片目录响应。
 *
 * <p>行契约与 {@code IF-DOR-11} {@link DoctorStationAdviceCatalogRowDto} 同构，仅入口强制中草药类别收窄。</p>
 */
public class PageTcmMedCatalogResponse extends PagedResult<DoctorStationAdviceCatalogRowDto> {
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response;

import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * IF-IP-TCM-35：中药饮片目录分页响应。
 *
 * <p>行契约与 {@code IF-IP-DOR-11} {@link InpatientDoctorStationAdviceCatalogRowDto} 同构，仅入口强制中草药类别收窄。</p>
 */
public class PageTcmHerbalMedicationCatalogResponse extends PagedResult<InpatientDoctorStationAdviceCatalogRowDto> {
}

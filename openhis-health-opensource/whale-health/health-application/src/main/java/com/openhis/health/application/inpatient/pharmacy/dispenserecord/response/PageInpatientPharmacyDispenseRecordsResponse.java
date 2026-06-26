/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.pharmacy.dispenserecord.response;

import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 分页查询住院发退药记录响应（IF-IPR-02）。
 */
public class PageInpatientPharmacyDispenseRecordsResponse
    extends PagedResult<InpatientPharmacyDispenseRecordRowDto> {
}

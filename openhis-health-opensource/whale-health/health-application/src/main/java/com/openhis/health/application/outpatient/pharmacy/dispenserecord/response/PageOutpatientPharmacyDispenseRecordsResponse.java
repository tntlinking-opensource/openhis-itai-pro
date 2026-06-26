/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.pharmacy.dispenserecord.response;

import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 分页查询门诊发退药记录响应（IF-OPR-02）。
 */
public class PageOutpatientPharmacyDispenseRecordsResponse
    extends PagedResult<PharmacyDispenseRecordRowDto> {
}

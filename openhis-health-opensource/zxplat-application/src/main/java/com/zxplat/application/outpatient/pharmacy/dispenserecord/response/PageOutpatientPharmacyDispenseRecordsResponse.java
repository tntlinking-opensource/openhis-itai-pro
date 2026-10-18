/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispenserecord.response;

import com.zxplat.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 分页查询门诊发退药记录响应（IF-OPR-02）。
 */
public class PageOutpatientPharmacyDispenseRecordsResponse
    extends PagedResult<PharmacyDispenseRecordRowDto> {
}

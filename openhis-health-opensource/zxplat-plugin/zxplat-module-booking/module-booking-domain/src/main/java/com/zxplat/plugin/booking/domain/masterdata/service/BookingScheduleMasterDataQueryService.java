/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.service;

import com.zxplat.plugin.booking.domain.masterdata.result.ScheduleFormMasterDataSnapshot;

/**
 * 门诊排班主数据查询；实现由 booking-bridge 提供。
 */
public interface BookingScheduleMasterDataQueryService {

    ScheduleFormMasterDataSnapshot loadOutpatientScheduleFormOptions(Long organizationId, Long practitionerId);

    default ScheduleFormMasterDataSnapshot loadOutpatientScheduleFormOptions(Long organizationId) {
        return loadOutpatientScheduleFormOptions(organizationId, null);
    }
}

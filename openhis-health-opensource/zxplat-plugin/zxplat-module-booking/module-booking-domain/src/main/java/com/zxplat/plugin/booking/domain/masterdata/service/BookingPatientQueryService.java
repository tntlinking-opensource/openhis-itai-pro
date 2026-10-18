/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.service;

import com.zxplat.plugin.booking.domain.masterdata.result.BookingPatientPageResult;
import com.zxplat.plugin.booking.domain.masterdata.queries.BookingPatientSearchQuery;

import java.util.List;
import java.util.Map;

/**
 * 患者主数据查询（预约登记台选患者）；实现由 booking-bridge 提供。
 */
public interface BookingPatientQueryService {

    BookingPatientPageResult pagePatientsForSelect(BookingPatientSearchQuery query);

    Map<Long, String> findPatientNamesByIds(List<Long> patientIds);
}

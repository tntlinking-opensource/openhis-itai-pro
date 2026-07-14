/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import com.openhis.whale.booking.domain.masterdata.result.BookingPatientPageResult;
import com.openhis.whale.booking.domain.masterdata.queries.BookingPatientSearchQuery;

import java.util.List;
import java.util.Map;

/**
 * 患者主数据查询（预约登记台选患者）；实现由 booking-bridge 提供。
 */
public interface BookingPatientQueryService {

    BookingPatientPageResult pagePatientsForSelect(BookingPatientSearchQuery query);

    Map<Long, String> findPatientNamesByIds(List<Long> patientIds);
}

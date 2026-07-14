/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import com.openhis.whale.booking.domain.medtech.result.BookableServiceRequestDto;

import java.util.List;

/**
 * 医技医嘱只读查询；实现由 booking-bridge 提供。
 */
public interface BookingServiceRequestQueryService {

    List<BookableServiceRequestDto> listBookableServiceRequestsByEncounter(Long encounterId);

    Long resolveActivityIdByServiceRequestId(Long serviceRequestId);

    Long resolveEncounterIdByServiceRequestId(Long serviceRequestId);
}

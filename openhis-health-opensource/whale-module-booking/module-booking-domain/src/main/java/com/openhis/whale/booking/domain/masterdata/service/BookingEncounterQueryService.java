/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import com.openhis.whale.booking.domain.masterdata.result.EncounterSearchResultDto;
import com.openhis.whale.booking.domain.masterdata.result.EncounterSummaryDto;
import com.openhis.whale.booking.domain.masterdata.queries.SearchEncounterQuery;

import java.util.Optional;

/**
 * 就诊主数据查询；实现由 booking-bridge 提供。
 */
public interface BookingEncounterQueryService {

    Optional<EncounterSummaryDto> loadEncounterSummary(Long encounterId);

    Optional<EncounterSearchResultDto> searchEncounter(SearchEncounterQuery query);
}

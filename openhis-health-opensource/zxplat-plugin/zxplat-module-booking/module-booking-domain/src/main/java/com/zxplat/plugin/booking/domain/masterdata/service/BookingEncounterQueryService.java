/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.service;

import com.zxplat.plugin.booking.domain.masterdata.result.EncounterSearchResultDto;
import com.zxplat.plugin.booking.domain.masterdata.result.EncounterSummaryDto;
import com.zxplat.plugin.booking.domain.masterdata.queries.SearchEncounterQuery;

import java.util.Optional;

/**
 * 就诊主数据查询；实现由 booking-bridge 提供。
 */
public interface BookingEncounterQueryService {

    Optional<EncounterSummaryDto> loadEncounterSummary(Long encounterId);

    Optional<EncounterSearchResultDto> searchEncounter(SearchEncounterQuery query);
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import com.openhis.whale.booking.domain.masterdata.result.BedLocationSummaryDto;
import com.openhis.whale.booking.domain.masterdata.model.MasterDataOption;

import java.util.List;
import java.util.Optional;

/**
 * 位置主数据查询（病区/床位/诊室）；实现由 booking-bridge 提供。
 */
public interface BookingLocationQueryService {

    List<MasterDataOption> listActiveLocationsByOrganization(Long organizationId);

    List<MasterDataOption> listWardsByOrganization(Long organizationId);

    List<BedLocationSummaryDto> listBedsUnderWard(Long wardLocationId);

    Optional<Long> resolveWardLocationIdByBedLocationId(Long bedLocationId);
}

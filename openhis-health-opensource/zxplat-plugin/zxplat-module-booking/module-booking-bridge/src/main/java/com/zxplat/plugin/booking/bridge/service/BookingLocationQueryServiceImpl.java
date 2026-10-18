/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.service;

import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.plugin.booking.domain.masterdata.result.BedLocationSummaryDto;
import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingLocationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingLocationQueryServiceImpl implements BookingLocationQueryService {

    private final LocationQueryService locationQueryService;

    @Override
    public List<MasterDataOption> listActiveLocationsByOrganization(Long organizationId) {
        List<Location> locations = locationQueryService.listActiveLocationsByOrganization(organizationId);
        return toMasterDataOptions(locations);
    }

    @Override
    public List<MasterDataOption> listWardsByOrganization(Long organizationId) {
        List<Location> wards = locationQueryService.listWardsByOrganization(organizationId);
        return toMasterDataOptions(wards);
    }

    @Override
    public List<BedLocationSummaryDto> listBedsUnderWard(Long wardLocationId) {
        List<BedLocationSummaryDto> beds = new ArrayList<>();
        if (wardLocationId == null) {
            return beds;
        }
        List<Long> bedIds = locationQueryService.listActiveBedIdsUnderWard(wardLocationId);
        for (Long bedId : bedIds) {
            locationQueryService.findOptionalById(bedId).ifPresent(location -> {
                if (location.getId() == null) {
                    return;
                }
                BedLocationSummaryDto dto = new BedLocationSummaryDto();
                dto.setBedLocationId(location.getId());
                dto.setBedName(location.getName());
                dto.setOperationalEnum(location.getOperationalEnum());
                beds.add(dto);
            });
        }
        return beds;
    }

    @Override
    public Optional<Long> resolveWardLocationIdByBedLocationId(Long bedLocationId) {
        if (bedLocationId == null) {
            return Optional.empty();
        }
        Long cursorId = bedLocationId;
        for (int depth = 0; depth < 5 && cursorId != null; depth++) {
            Optional<Location> locationOptional = locationQueryService.findOptionalById(cursorId);
            if (locationOptional.isEmpty()) {
                return Optional.empty();
            }
            Location location = locationOptional.get();
            if (LocationForm.WARD.getCode().equals(location.getFormEnum())) {
                return Optional.of(location.getId());
            }
            cursorId = location.getParentId();
        }
        return Optional.empty();
    }

    private List<MasterDataOption> toMasterDataOptions(List<Location> locations) {
        List<MasterDataOption> options = new ArrayList<>();
        if (locations == null) {
            return options;
        }
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            MasterDataOption option = new MasterDataOption();
            option.setSort(location.getId());
            option.setValue(String.valueOf(location.getId()));
            option.setLabel(location.getName());
            options.add(option);
        }
        return options;
    }
}

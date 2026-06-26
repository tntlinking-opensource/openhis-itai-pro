package com.openhis.whale.booking.adapter.reader;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.whale.booking.contracts.dto.BedLocationSummaryDto;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.reader.HealthLocationReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link HealthLocationReader} 实现：床位/病区/诊室 Location 读侧。
 *
 * <p>供床位预约、排班诊室下拉；床位列表含 operational 状态。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthLocationReaderAdapter implements HealthLocationReader {

    private final LocationQueryService locationQueryService;

    @Override
    public List<MasterDataOption> listActiveLocationsByOrganization(Long organizationId) {
        List<Location> locations = locationQueryService.listActiveLocationsByOrganization(organizationId);
        return this.toMasterDataOptions(locations);
    }

    @Override
    public List<MasterDataOption> listWardsByOrganization(Long organizationId) {
        List<Location> wards = locationQueryService.listWardsByOrganization(organizationId);
        return this.toMasterDataOptions(wards);
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
        // 沿 parent 链向上查找 form=WARD，最多 5 层防环。
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

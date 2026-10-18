/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.service;

import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.constants.PlatformRoleCodeConstants;
import com.zxplat.plugin.booking.bridge.masterdata.context.BookingPractitionerHealthcareContextLoader;
import com.zxplat.plugin.booking.bridge.masterdata.context.BookingPractitionerRoleScopeContextLoader;
import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import com.zxplat.plugin.booking.domain.masterdata.result.ScheduleFormMasterDataSnapshot;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingScheduleMasterDataQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingScheduleMasterDataQueryServiceImpl implements BookingScheduleMasterDataQueryService {

    private final OrganizationQueryService organizationQueryService;
    private final BookingPractitionerRoleScopeContextLoader practitionerRoleScopeLoader;
    private final LocationQueryService locationQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final BookingPractitionerHealthcareContextLoader practitionerHealthcareServiceSupport;

    @Override
    public ScheduleFormMasterDataSnapshot loadOutpatientScheduleFormOptions(Long organizationId, Long practitionerId) {
        ScheduleFormMasterDataSnapshot snapshot = new ScheduleFormMasterDataSnapshot();
        snapshot.setDepartments(this.toOrganizationOptions(organizationQueryService.listOrganizationsForOutpatientRegistration()));
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
            PlatformRoleCodeConstants.ROLE_DOCTOR, organizationId);
        snapshot.setPractitioners(this.toPractitionerOptions(practitioners));
        if (organizationId != null) {
            snapshot.setClinicLocations(this.toLocationOptions(locationQueryService.listActiveLocationsByOrganization(organizationId)));
        } else {
            snapshot.setClinicLocations(new ArrayList<>());
        }
        List<HealthcareService> services = healthcareServiceQueryService.listServicesForRegistration();
        services = practitionerHealthcareServiceSupport.narrowByPractitioner(services, practitionerId);
        snapshot.setHealthcareServices(this.toHealthcareServiceOptions(services));
        return snapshot;
    }

    private List<MasterDataOption> toOrganizationOptions(List<Organization> organizations) {
        List<MasterDataOption> options = new ArrayList<>();
        if (organizations == null) {
            return options;
        }
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            MasterDataOption option = new MasterDataOption();
            option.setSort(organization.getId());
            option.setValue(String.valueOf(organization.getId()));
            option.setLabel(organization.getName());
            options.add(option);
        }
        return options;
    }

    private List<MasterDataOption> toPractitionerOptions(List<Practitioner> practitioners) {
        List<MasterDataOption> options = new ArrayList<>();
        if (practitioners == null) {
            return options;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            MasterDataOption option = new MasterDataOption();
            option.setSort(practitioner.getId());
            option.setValue(String.valueOf(practitioner.getId()));
            option.setLabel(practitioner.getName());
            options.add(option);
        }
        return options;
    }

    private List<MasterDataOption> toLocationOptions(List<Location> locations) {
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

    private List<MasterDataOption> toHealthcareServiceOptions(List<HealthcareService> services) {
        List<MasterDataOption> options = new ArrayList<>();
        if (services == null) {
            return options;
        }
        for (HealthcareService service : services) {
            if (service == null || service.getId() == null) {
                continue;
            }
            MasterDataOption option = new MasterDataOption();
            option.setSort(service.getId());
            option.setValue(String.valueOf(service.getId()));
            option.setLabel(service.getName());
            options.add(option);
        }
        return options;
    }
}

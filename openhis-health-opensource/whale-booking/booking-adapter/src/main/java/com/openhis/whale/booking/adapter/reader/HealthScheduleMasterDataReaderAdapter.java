package com.openhis.whale.booking.adapter.reader;

import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.contracts.reader.HealthScheduleMasterDataReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link HealthScheduleMasterDataReader} 实现（排班/预约表单下拉）。
 *
 * <p>读 health 主数据（Organization/Practitioner/Location/HealthcareService），不写 booking 聚合。</p>
 * <p>医师列表与挂号台同源（{@link PractitionerRoleScopeLoader}），保证排班筛选口径一致。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthScheduleMasterDataReaderAdapter implements HealthScheduleMasterDataReader {

    private final OrganizationQueryService organizationQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final LocationQueryService locationQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    @Override
    public ScheduleFormMasterDataSnapshot loadOutpatientScheduleFormOptions(Long organizationId) {
        ScheduleFormMasterDataSnapshot snapshot = new ScheduleFormMasterDataSnapshot();
        // 科室列表与挂号台同源，保证排班筛选与挂号口径一致。
        snapshot.setDepartments(this.toOrganizationOptions(organizationQueryService.listOrganizationsForOutpatientRegistration()));
        // 医师按科室岗位收窄；organizationId 为空时返回全院可排班医生。
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR, organizationId);
        snapshot.setPractitioners(this.toPractitionerOptions(practitioners));
        // 诊室：机构下全部启用位置（P0 不按形态再收窄）。
        if (organizationId != null) {
            snapshot.setClinicLocations(this.toLocationOptions(locationQueryService.listActiveLocationsByOrganization(organizationId)));
        } else {
            snapshot.setClinicLocations(new ArrayList<>());
        }
        snapshot.setHealthcareServices(this.toHealthcareServiceOptions(healthcareServiceQueryService.listServicesForRegistration()));
        return snapshot;
    }

    /**
     * 科室实体转下拉选项。
     */
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

    /**
     * 医师实体转下拉选项。
     */
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

    /**
     * 位置实体转下拉选项。
     */
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

    /**
     * 医疗服务实体转下拉选项。
     */
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

package com.openhis.health.bridge.shared.identity;

import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.ILoginOrganizationScopeQuery;
import com.openhis.whale.core.application.dto.LoginOrganizationOption;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 登录选医院/科室：基于 {@code adm_organization} 树解析医院归属。
 */
@Service
@RequiredArgsConstructor
public class LoginOrganizationScopeQueryService implements ILoginOrganizationScopeQuery {

    private final OrganizationQueryService organizationQueryService;

    @Override
    public List<LoginOrganizationOption> listActiveHospitalOrganizations() {
        List<LoginOrganizationOption> options = new ArrayList<>();
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (!isActiveHospital(organization)) {
                continue;
            }
            options.add(toOption(organization));
        }
        return options;
    }

    @Override
    public Optional<Long> resolveHospitalOrganizationId(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        return walkToHospital(organization, new LinkedHashSet<>());
    }

    @Override
    public boolean belongsToHospital(Long businessOrganizationId, Long hospitalOrganizationId) {
        if (businessOrganizationId == null || hospitalOrganizationId == null) {
            return false;
        }
        if (businessOrganizationId.equals(hospitalOrganizationId)) {
            Organization organization =
                organizationQueryService.loadOrganizationByIdForDisplay(hospitalOrganizationId);
            return isActiveHospital(organization);
        }
        return resolveHospitalOrganizationId(businessOrganizationId)
            .map(hospitalOrganizationId::equals)
            .orElse(false);
    }

    @Override
    public Optional<LoginOrganizationOption> loadOrganizationOption(Long businessOrganizationId) {
        if (businessOrganizationId == null) {
            return Optional.empty();
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(businessOrganizationId);
        if (organization == null) {
            return Optional.empty();
        }
        return Optional.of(toOption(organization));
    }

    private Optional<Long> walkToHospital(Organization organization, Set<Long> visited) {
        if (organization == null || organization.getId() == null) {
            return Optional.empty();
        }
        if (!visited.add(organization.getId())) {
            return Optional.empty();
        }
        OrganizationType type = OrganizationType.resolveRaw(organization.getTypeEnum());
        if (OrganizationType.HOSPITAL == type) {
            return Optional.of(organization.getId());
        }
        if (organization.getParentId() == null) {
            return Optional.empty();
        }
        Organization parent =
            organizationQueryService.loadOrganizationByIdForDisplay(organization.getParentId());
        return walkToHospital(parent, visited);
    }

    private static boolean isActiveHospital(Organization organization) {
        return organization != null
            && OrganizationType.HOSPITAL == OrganizationType.resolveRaw(organization.getTypeEnum())
            && PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum());
    }

    private static LoginOrganizationOption toOption(Organization organization) {
        return new LoginOrganizationOption(
            organization.getId(),
            StringUtils.defaultString(organization.getBusNo()),
            StringUtils.defaultString(organization.getName()));
    }
}

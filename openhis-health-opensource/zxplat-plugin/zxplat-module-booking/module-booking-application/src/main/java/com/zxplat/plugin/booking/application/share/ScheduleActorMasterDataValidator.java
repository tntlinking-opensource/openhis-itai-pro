/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.share;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ScheduleActorKind;
import com.zxplat.plugin.booking.application.outpatient.request.ScheduleActorRequest;
import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import com.zxplat.plugin.booking.domain.masterdata.result.ScheduleFormMasterDataSnapshot;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingPractitionerHealthcareQueryService;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingScheduleMasterDataQueryService;
import com.zxplat.plugin.booking.domain.schedule.command.CreateScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊 Schedule Actor 主数据引用校验（07 §2.2 BR-SCH-001～005）。
 *
 * <p>经 {@link BookingScheduleMasterDataQueryService} 读 health 主数据，校验机构与参与者引用合法。</p>
 */
@Component
@RequiredArgsConstructor
public class ScheduleActorMasterDataValidator {

    private static final int PRACTITIONER_TYPE = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
    private static final int LOCATION_TYPE = ScheduleActorKind.LOCATION.getSortOrder().intValue();
    private static final int HEALTHCARE_SERVICE_TYPE = ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue();

    private final BookingScheduleMasterDataQueryService bookingScheduleMasterDataQueryService;
    private final BookingPractitionerHealthcareQueryService bookingPractitionerHealthcareQueryService;

    /**
     * 校验创建/编辑日程时的机构与参与者主数据引用。
     *
     * @param organizationId 门诊科室主键
     * @param actors         参与者请求列表
     * @throws BusinessException 机构非门诊科室或参与者不在合法范围时
     */
    public void assertActorsReferenceValid(Long organizationId, List<ScheduleActorRequest> actors) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        ScheduleFormMasterDataSnapshot snapshot = bookingScheduleMasterDataQueryService.loadOutpatientScheduleFormOptions(organizationId);
        this.assertOrganizationIsClinic(organizationId, snapshot);
        this.assertActorIdsInSnapshot(snapshot, actors);
        this.assertPractitionerHealthcareServicePairValid(actors);
    }

    /**
     * 校验领域创建命令中的参与者引用（岗位模板生成等内部路径）。
     *
     * @param organizationId 门诊科室主键
     * @param actors         领域参与者命令列表
     */
    public void assertCreateCommandActorsReferenceValid(
        Long organizationId,
        List<CreateScheduleCommand.ScheduleActorItemCommand> actors) {
        List<ScheduleActorRequest> requests = new ArrayList<>();
        if (actors != null) {
            for (CreateScheduleCommand.ScheduleActorItemCommand actor : actors) {
                ScheduleActorRequest request = new ScheduleActorRequest();
                request.setActorType(actor.getActorType());
                request.setActorId(actor.getActorId());
                requests.add(request);
            }
        }
        this.assertActorsReferenceValid(organizationId, requests);
    }

    /**
     * 校验读模型参与者引用（发布前）。
     *
     * @param organizationId 门诊科室主键
     * @param actors         日程参与者读模型
     */
    public void assertActorModelsReferenceValid(Long organizationId, List<ScheduleActorModel> actors) {
        List<ScheduleActorRequest> requests = new ArrayList<>();
        if (actors != null) {
            for (ScheduleActorModel actor : actors) {
                ScheduleActorRequest request = new ScheduleActorRequest();
                request.setActorType(actor.getActorType());
                request.setActorId(actor.getActorId());
                requests.add(request);
            }
        }
        this.assertActorsReferenceValid(organizationId, requests);
    }

    /**
     * 校验单个医师引用合法。
     */
    public void assertPractitionerReferenceValid(Long organizationId, Long practitionerId) {
        if (practitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "substitutePractitionerId 不能为空");
        }
        ScheduleActorRequest request = new ScheduleActorRequest();
        request.setActorType(PRACTITIONER_TYPE);
        request.setActorId(practitionerId);
        this.assertActorsReferenceValid(organizationId, List.of(request));
    }

    private void assertOrganizationIsClinic(Long organizationId, ScheduleFormMasterDataSnapshot snapshot) {
        Set<Long> departmentIds = this.toIdSet(snapshot.getDepartments());
        if (!departmentIds.contains(organizationId)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 须为门诊科室");
        }
    }

    private void assertActorIdsInSnapshot(ScheduleFormMasterDataSnapshot snapshot, List<ScheduleActorRequest> actors) {
        if (actors == null || actors.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = this.toIdSet(snapshot.getPractitioners());
        Set<Long> locationIds = this.toIdSet(snapshot.getClinicLocations());
        Set<Long> serviceIds = this.toIdSet(snapshot.getHealthcareServices());
        for (ScheduleActorRequest actor : actors) {
            if (actor == null || actor.getActorType() == null || actor.getActorId() == null) {
                continue;
            }
            if (actor.getActorType() == PRACTITIONER_TYPE) {
                if (!practitionerIds.contains(actor.getActorId())) {
                    throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "医师不在该科室有效岗位范围内");
                }
            }
            if (actor.getActorType() == LOCATION_TYPE) {
                if (!locationIds.contains(actor.getActorId())) {
                    throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "诊室不属于该科室");
                }
            }
            if (actor.getActorType() == HEALTHCARE_SERVICE_TYPE) {
                if (!serviceIds.contains(actor.getActorId())) {
                    throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "号源类型不可用");
                }
            }
        }
    }

    private Set<Long> toIdSet(List<MasterDataOption> options) {
        Set<Long> ids = new HashSet<>();
        if (options == null) {
            return ids;
        }
        for (MasterDataOption option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(option.getValue()));
            } catch (NumberFormatException ignored) {
                // 跳过非法 value，不影响其余选项校验。
            }
        }
        return ids;
    }

    /**
     * 绑定了医师的日程须使用医师可接诊号源；科室号（无医师）不在此校验。
     */
    private void assertPractitionerHealthcareServicePairValid(List<ScheduleActorRequest> actors) {
        if (actors == null || actors.isEmpty()) {
            return;
        }
        Long practitionerId = null;
        Long healthcareServiceId = null;
        for (ScheduleActorRequest actor : actors) {
            if (actor == null || actor.getActorType() == null || actor.getActorId() == null) {
                continue;
            }
            if (actor.getActorType() == PRACTITIONER_TYPE) {
                practitionerId = actor.getActorId();
            }
            if (actor.getActorType() == HEALTHCARE_SERVICE_TYPE) {
                healthcareServiceId = actor.getActorId();
            }
        }
        if (practitionerId != null && healthcareServiceId != null) {
            bookingPractitionerHealthcareQueryService.assertPractitionerAcceptsHealthcareService(
                practitionerId, healthcareServiceId);
        }
    }
}

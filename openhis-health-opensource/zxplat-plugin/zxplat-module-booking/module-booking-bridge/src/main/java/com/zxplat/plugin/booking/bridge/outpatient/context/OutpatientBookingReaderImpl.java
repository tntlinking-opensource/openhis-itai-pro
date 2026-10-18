/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.outpatient.context;

import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ScheduleActorKind;
import com.zxplat.plugin.booking.bridge.slot.context.BookingSlotReaderImpl;
import com.zxplat.plugin.booking.domain.appointment.command.BookingRegistrationValidateCommand;
import com.zxplat.plugin.booking.domain.appointment.result.BookableServiceOption;
import com.zxplat.plugin.booking.domain.slot.result.BookableSlotHint;
import com.zxplat.plugin.booking.domain.appointment.result.OutpatientBookingSnapshot;
import com.zxplat.plugin.booking.domain.appointment.queries.OutpatientBookingLookupQuery;
import com.zxplat.plugin.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.zxplat.plugin.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.zxplat.plugin.booking.domain.appointment.service.AppointmentQueryService;
import com.zxplat.plugin.booking.domain.schedule.entity.Schedule;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.zxplat.plugin.booking.domain.schedule.repository.ScheduleRepository;
import com.zxplat.plugin.booking.domain.schedule.service.ScheduleQueryService;
import com.zxplat.plugin.booking.domain.slot.queries.model.BookableSlotModel;
import com.zxplat.plugin.booking.domain.slot.queries.model.SlotDetailModel;
import com.zxplat.plugin.booking.domain.slot.service.SlotQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * DEP-REG 挂号读侧（orchestrator 专用）。
 *
 * <p>职责边界：health 挂号台读预约快照、可约号源类型/时段、挂号前三要素校验（科室/医师/号源类型）。</p>
 * <p>依赖 booking-domain 读服务 + health 主数据展示名；禁止依赖 booking-application。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientBookingReaderImpl {

    /** 预约已转挂号，不可重复加载（IF-REG 衔接）。 */
    private static final String BKG_REG_409 = "BKG-REG-409";
    /** 挂号提交与预约快照不一致（患者/三要素）。 */
    private static final String BKG_REG_403 = "BKG-REG-403";

    private final AppointmentQueryService appointmentQueryService;
    private final BookingSlotReaderImpl bookingSlotReader;
    private final SlotQueryService slotQueryService;
    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleRepository scheduleRepository;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /**
     * 按患者/业务号/日期查找活跃门诊预约（未转挂号）。
     *
     * @param query 查找条件
     * @return 预约快照；无匹配 empty
     * @throws BusinessException BKG-REG-409 已转挂号
     */
    public Optional<OutpatientBookingSnapshot> findActiveOutpatientBooking(OutpatientBookingLookupQuery query) {
        if (query == null) {
            return Optional.empty();
        }
        ActiveOutpatientBookingLookupQuery domainQuery = new ActiveOutpatientBookingLookupQuery();
        domainQuery.setPatientId(query.getPatientId());
        domainQuery.setAppointmentBusNo(query.getAppointmentBusNo());
        domainQuery.setRegistrationDate(query.getRegistrationDate());

        Optional<AppointmentBookingDetailModel> bookingOptional =
            appointmentQueryService.findActiveOutpatientBookingForRegister(domainQuery);
        if (bookingOptional.isEmpty()) {
            return Optional.empty();
        }
        AppointmentBookingDetailModel booking = bookingOptional.get();
        // 已关联 encounter 视为已挂号，拒绝再次加载预约态。
        if (booking.getEncounterId() != null) {
            throw new BusinessException(BKG_REG_409, "预约已转挂号，不可重复加载");
        }
        return Optional.of(this.toSnapshot(booking));
    }

    public Optional<OutpatientBookingSnapshot> loadOutpatientBookingById(Long appointmentId) {
        if (appointmentId == null) {
            return Optional.empty();
        }
        try {
            AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(appointmentId);
            if (booking.getEncounterId() != null) {
                throw new BusinessException(BKG_REG_409, "预约已转挂号");
            }
            return Optional.of(this.toSnapshot(booking));
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.APPOINTMENT_NOT_FOUND.equals(ex.getErrorCode())) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    public List<BookableServiceOption> listBookableServiceTypes(
        Long organizationId, Long practitionerId, LocalDate date) {
        if (organizationId == null || date == null) {
            return new ArrayList<>();
        }
        Set<Long> serviceIds = bookingSlotReader.findHealthcareServiceIdsOnActiveSchedules(
            organizationId, date, practitionerId);
        List<BookableServiceOption> options = new ArrayList<>();
        for (Long serviceId : serviceIds) {
            HealthcareService service = healthcareServiceQueryService.loadHealthcareServiceById(serviceId);
            if (service == null || service.getId() == null) {
                continue;
            }
            BookableServiceOption option = new BookableServiceOption();
            option.setHealthcareServiceId(service.getId());
            option.setServiceTypeName(service.getName());
            options.add(option);
        }
        return options;
    }

    public List<BookableSlotHint> listBookableSlots(
        Long organizationId, Long practitionerId, Long healthcareServiceId, LocalDate date) {
        if (organizationId == null || date == null) {
            return new ArrayList<>();
        }
        List<BookableSlotModel> slots = bookingSlotReader.findAvailableSlots(
            organizationId, date, practitionerId, healthcareServiceId);
        List<BookableSlotHint> hints = new ArrayList<>();
        for (BookableSlotModel slot : slots) {
            BookableSlotHint hint = new BookableSlotHint();
            hint.setSlotId(slot.getSlotId());
            hint.setSlotStart(slot.getStartTime());
            hint.setSlotEnd(slot.getEndTime());
            hint.setRemainingCount(this.resolveRemainingCount(slot.getSlotId()));
            hints.add(hint);
        }
        return hints;
    }

    private Integer resolveRemainingCount(Long slotId) {
        if (slotId == null) {
            return null;
        }
        SlotDetailModel slot = slotQueryService.loadSlotById(slotId);
        if (slot == null || slot.getCapacity() == null) {
            return null;
        }
        int booked;
        if (slot.getBookedCount() != null) {
            booked = slot.getBookedCount();
        } else {
            booked = 0;
        }
        return Math.max(slot.getCapacity() - booked, 0);
    }

    public void validateForRegistration(BookingRegistrationValidateCommand command) {
        if (command == null || command.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        AppointmentBookingDetailModel booking = appointmentQueryService.loadOutpatientBookingById(command.getAppointmentId());
        if (booking.getEncounterId() != null) {
            throw new BusinessException(BKG_REG_409, "预约已转挂号");
        }
        OutpatientBookingSnapshot snapshot = this.toSnapshot(booking);
        // Gate：挂号提交患者须与预约一致。
        if (command.getPatientId() != null && !Objects.equals(command.getPatientId(), booking.getPatientId())) {
            throw new BusinessException(BKG_REG_403, "预约患者与挂号提交不一致");
        }
        // Gate：科室/医师/号源类型三要素须与预约快照一致。
        this.assertThreeElementsMatch(command, snapshot);
    }

    /** 校验挂号提交与预约快照的科室、医师、号源类型一致（BKG-REG-403）。 */
    private void assertThreeElementsMatch(BookingRegistrationValidateCommand command, OutpatientBookingSnapshot snapshot) {
        if (!Objects.equals(command.getOrganizationId(), snapshot.getOrganizationId())) {
            throw new BusinessException(BKG_REG_403, "预约科室与挂号提交不一致");
        }
        if (!Objects.equals(command.getPractitionerId(), snapshot.getPractitionerId())) {
            throw new BusinessException(BKG_REG_403, "预约医师与挂号提交不一致");
        }
        if (!Objects.equals(command.getHealthcareServiceId(), snapshot.getHealthcareServiceId())) {
            throw new BusinessException(BKG_REG_403, "预约号源类型与挂号提交不一致");
        }
    }

    private OutpatientBookingSnapshot toSnapshot(AppointmentBookingDetailModel booking) {
        Long scheduleId = booking.getScheduleId();
        Long practitionerId = booking.getPractitionerId();
        Long healthcareServiceId = booking.getHealthcareServiceId();

        if (booking.getSlotId() != null) {
            SlotDetailModel slot = slotQueryService.loadSlotById(booking.getSlotId());
            if (slot != null && slot.getScheduleId() != null) {
                scheduleId = slot.getScheduleId();
            }
        }
        if (scheduleId != null) {
            Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
            if (schedule != null) {
                healthcareServiceId = schedule.getHealthcareServiceId();
                // 替诊生效：挂号带入接诊医师须为顶班医师，不是 Actor 原出诊医师。
                if (schedule.getSubstitutePractitionerId() != null) {
                    practitionerId = schedule.getSubstitutePractitionerId();
                } else {
                    ScheduleDetailModel scheduleDetail = scheduleQueryService.loadScheduleById(scheduleId);
                    practitionerId = this.resolvePractitionerId(scheduleDetail);
                }
            } else {
                ScheduleDetailModel scheduleDetail = scheduleQueryService.loadScheduleById(scheduleId);
                practitionerId = this.resolvePractitionerId(scheduleDetail);
            }
        }

        OutpatientBookingSnapshot snapshot = new OutpatientBookingSnapshot();
        snapshot.setAppointmentId(booking.getId());
        snapshot.setAppointmentBusNo(booking.getBusNo());
        snapshot.setSlotId(booking.getSlotId());
        snapshot.setOrganizationId(booking.getOrganizationId());
        snapshot.setPractitionerId(practitionerId);
        snapshot.setHealthcareServiceId(healthcareServiceId);
        snapshot.setSlotStart(booking.getStartInstant());
        snapshot.setSlotEnd(booking.getEndInstant());
        snapshot.setEncounterId(booking.getEncounterId());
        snapshot.setStatusEnum(booking.getStatusView());
        this.enrichDisplayNames(snapshot);
        return snapshot;
    }

    /** 从日程 Actor 解析原出诊医师（无替诊时用于挂号快照）。 */
    private Long resolvePractitionerId(ScheduleDetailModel scheduleDetail) {
        if (scheduleDetail == null || scheduleDetail.getActors() == null) {
            return null;
        }
        Integer practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        for (ScheduleActorModel actor : scheduleDetail.getActors()) {
            if (practitionerType.equals(actor.getActorType())) {
                return actor.getActorId();
            }
        }
        return null;
    }

    private void enrichDisplayNames(OutpatientBookingSnapshot snapshot) {
        if (snapshot.getOrganizationId() != null) {
            Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(snapshot.getOrganizationId());
            if (organization != null && StringUtils.isNotBlank(organization.getName())) {
                snapshot.setOrganizationName(organization.getName());
            }
        }
        if (snapshot.getPractitionerId() != null) {
            Practitioner practitioner = practitionerQueryService.findById(snapshot.getPractitionerId());
            if (practitioner != null && StringUtils.isNotBlank(practitioner.getName())) {
                snapshot.setPractitionerName(practitioner.getName());
            }
        }
        if (snapshot.getHealthcareServiceId() != null) {
            HealthcareService service = healthcareServiceQueryService.loadHealthcareServiceById(snapshot.getHealthcareServiceId());
            if (service != null && StringUtils.isNotBlank(service.getName())) {
                snapshot.setServiceTypeName(service.getName());
            }
        }
    }
}

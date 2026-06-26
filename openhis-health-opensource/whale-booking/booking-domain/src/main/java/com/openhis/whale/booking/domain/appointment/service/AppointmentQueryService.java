/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.booking.domain.appointment.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.appointment.entity.Appointment;
import com.openhis.whale.booking.domain.appointment.queries.AppointmentPageQuery;
import com.openhis.whale.booking.domain.appointment.queries.ActiveOutpatientBookingLookupQuery;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBatchModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingDetailModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentBookingListModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentQueryModel;
import com.openhis.whale.booking.domain.appointment.queries.model.AppointmentQueuePageModel;
import com.openhis.whale.booking.domain.appointment.queries.model.PatientAppointmentModel;
import com.openhis.whale.booking.domain.appointment.repository.AppointmentRepository;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
import com.openhis.whale.booking.domain.share.AppointmentKindConstants;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 预约与候诊读侧查询服务。
 *
 * <p>约束：只读查询，不写库；默认过滤 {@code isDeleted=false}。</p>
 */
@Service
@RequiredArgsConstructor
public class AppointmentQueryService {

    private static final String OUTPATIENT_BOOKING_KIND = AppointmentKindConstants.OUTPATIENT_BOOKING;
    private static final String DIAGNOSTIC_BOOKING_KIND = AppointmentKindConstants.DIAGNOSTIC_BOOKING;
    private static final String BED_BOOKING_KIND = AppointmentKindConstants.BED_BOOKING;

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final ScheduleRepository scheduleRepository;

    /**
     * 分页查询有效门诊预约列表。
     *
     * @param query 分页条件
     * @return 分页列表行
     */
    public Page<AppointmentBookingListModel> pageOutpatientBookings(AppointmentPageQuery query) {
        // 分页查询须绑定机构，防止跨机构数据泄露
        if (query == null || query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (query.getPageNo() == null || query.getPageNo() <= 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "pageNo 非法");
        }
        if (query.getPageSize() == null || query.getPageSize() <= 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "pageSize 非法");
        }

        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            predicates.add(cb.equal(root.get("appointmentKind"), OUTPATIENT_BOOKING_KIND));
            if (query.getPatientId() != null) {
                predicates.add(cb.equal(root.get("patientId"), query.getPatientId()));
            }
            if (query.getDateFrom() != null) {
                LocalDateTime from = query.getDateFrom().atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("startInstant"), from));
            }
            if (query.getDateTo() != null) {
                LocalDateTime toExclusive = query.getDateTo().plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("startInstant"), toExclusive));
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get("busNo")), likeIc));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable pageable = PageRequest.of(query.getPageNo() - 1, query.getPageSize(),
            Sort.by(Sort.Direction.DESC, "startInstant"));
        return appointmentRepository.findAll(specification, pageable).map(this::toBookingListModel);
    }

    /**
     * IF-REG-12：按患者/预约单号查当日有效门诊预约（未转挂号）。
     *
     * @param query 检索条件
     * @return 有效预约详情；无则 empty
     */
    public Optional<AppointmentBookingDetailModel> findActiveOutpatientBookingForRegister(ActiveOutpatientBookingLookupQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "查询条件不能为空");
        }
        LocalDate registrationDate = query.getRegistrationDate();
        // 未传挂号日时默认查当日预约
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
        boolean hasBusNo = StringUtils.isNotBlank(query.getAppointmentBusNo());
        // 患者与单号至少其一，否则无法唯一定位预约
        if (!hasBusNo && query.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "patientId 与 appointmentBusNo 至少传一个");
        }

        LocalDateTime from = registrationDate.atStartOfDay();
        LocalDateTime toExclusive = registrationDate.plusDays(1).atStartOfDay();
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get("appointmentKind"), OUTPATIENT_BOOKING_KIND));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startInstant"), from));
            predicates.add(cb.lessThan(root.get("startInstant"), toExclusive));
            if (hasBusNo) {
                predicates.add(cb.equal(cb.lower(root.get("busNo")), query.getAppointmentBusNo().trim().toLowerCase()));
            } else {
                // 无单号时按患者收窄当日预约
                predicates.add(cb.equal(root.get("patientId"), query.getPatientId()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Appointment> rows = appointmentRepository.findAll(specification,
            Sort.by(Sort.Direction.ASC, "startInstant"));
        // 当日无有效预约时返回空，由挂号台引导现场处理
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.toBookingDetailModel(rows.get(0)));
    }

    /**
     * 按主键加载门诊预约详情（仅有效项）。
     *
     * @param appointmentId 预约主键
     * @return 详情模型
     */
    public AppointmentBookingDetailModel loadOutpatientBookingById(Long appointmentId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "预约不存在"));
        // 已软删视为终态，不可再作有效预约加载
        if (Boolean.TRUE.equals(appointment.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "预约已取消或终态");
        }
        return this.toBookingDetailModel(appointment);
    }

    /**
     * 按主键加载有效医技预约详情。
     *
     * @param appointmentId 预约主键
     * @return 医技预约详情
     * @throws BusinessException 非医技形态时
     */
    public AppointmentBookingDetailModel loadDiagnosticBookingById(Long appointmentId) {
        AppointmentBookingDetailModel model = this.loadOutpatientBookingById(appointmentId);
        if (!DIAGNOSTIC_BOOKING_KIND.equals(model.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非医技预约");
        }
        return model;
    }

    /**
     * 按主键加载有效床位预约详情。
     *
     * @param appointmentId 预约主键
     * @return 床位预约详情
     * @throws BusinessException 非床位形态时
     */
    public AppointmentBookingDetailModel loadBedBookingById(Long appointmentId) {
        AppointmentBookingDetailModel model = this.loadOutpatientBookingById(appointmentId);
        if (!BED_BOOKING_KIND.equals(model.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非床位预约");
        }
        return model;
    }

    /**
     * 批量解析医嘱对应的有效医技预约主键（appointmentTypeCode 存 serviceRequestId）。
     *
     * @param serviceRequestIds 医嘱主键集合
     * @return 医嘱 id → 医技预约 id
     */
    public Map<Long, Long> mapActiveDiagnosticAppointmentIdsByServiceRequestIds(Collection<Long> serviceRequestIds) {
        Map<Long, Long> result = new HashMap<>();
        if (serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return result;
        }
        Set<String> typeCodes = new HashSet<>();
        for (Long serviceRequestId : serviceRequestIds) {
            if (serviceRequestId != null) {
                typeCodes.add(String.valueOf(serviceRequestId));
            }
        }
        if (typeCodes.isEmpty()) {
            return result;
        }
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get("appointmentKind"), DIAGNOSTIC_BOOKING_KIND));
            predicates.add(root.get("appointmentTypeCode").in(typeCodes));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Appointment> rows = appointmentRepository.findAll(specification);
        for (Appointment appointment : rows) {
            if (appointment.getAppointmentTypeCode() == null || appointment.getId() == null) {
                continue;
            }
            try {
                Long serviceRequestId = Long.valueOf(appointment.getAppointmentTypeCode());
                result.putIfAbsent(serviceRequestId, appointment.getId());
            } catch (NumberFormatException ignored) {
                // 忽略非数字 appointmentTypeCode
            }
        }
        return result;
    }

    /**
     * 分页查询有效医技预约列表。
     *
     * @param organizationId 机构主键
     * @param encounterId 就诊主键（可选）
     * @param pageNo 页码（1-based）
     * @param pageSize 页大小
     * @return 医技预约分页
     */
    public Page<AppointmentBookingListModel> pageDiagnosticBookings(
        Long organizationId, Long encounterId, Integer pageNo, Integer pageSize) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (pageNo == null || pageNo <= 0 || pageSize == null || pageSize <= 0) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "分页参数非法");
        }
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("appointmentKind"), DIAGNOSTIC_BOOKING_KIND));
            if (encounterId != null) {
                predicates.add(cb.equal(root.get("encounterId"), encounterId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "startInstant"));
        return appointmentRepository.findAll(specification, pageable).map(this::toBookingListModel);
    }

    /**
     * 重复预约校验：同一医嘱仅允许一条有效医技预约。
     *
     * @param serviceRequestId 医嘱主键
     * @return 已存在预约详情；无则 empty
     */
    public Optional<AppointmentBookingDetailModel> findActiveDiagnosticBookingByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return Optional.empty();
        }
        Map<Long, Long> mapped = this.mapActiveDiagnosticAppointmentIdsByServiceRequestIds(List.of(serviceRequestId));
        Long appointmentId = mapped.get(serviceRequestId);
        if (appointmentId == null) {
            return Optional.empty();
        }
        return Optional.of(this.loadDiagnosticBookingById(appointmentId));
    }

    /**
     * 重复预约校验：同一床位同一意向入院日仅允许一条有效床位预约。
     *
     * @param bedLocationId 床位库位主键
     * @param intendedDate 意向入院日
     * @return 已存在预约详情；无则 empty
     */
    public Optional<AppointmentBookingDetailModel> findActiveBedBookingByBedAndDate(Long bedLocationId, LocalDate intendedDate) {
        if (bedLocationId == null || intendedDate == null) {
            return Optional.empty();
        }
        LocalDateTime from = intendedDate.atStartOfDay();
        LocalDateTime toExclusive = intendedDate.plusDays(1).atStartOfDay();
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get("appointmentKind"), BED_BOOKING_KIND));
            predicates.add(cb.equal(root.get("destinationLocationId"), bedLocationId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startInstant"), from));
            predicates.add(cb.lessThan(root.get("startInstant"), toExclusive));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Appointment> rows = appointmentRepository.findAll(specification,
            Sort.by(Sort.Direction.DESC, "startInstant"));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.toBookingDetailModel(rows.get(0)));
    }

    /**
     * 按主键或业务单号加载有效体检预约。
     *
     * @param organizationId 机构主键
     * @param appointmentId 预约主键（可选）
     * @param busNo 业务单号（可选）
     * @return 详情模型
     */
    public AppointmentBookingDetailModel loadActiveHealthCheckupBooking(
        Long organizationId, Long appointmentId, String busNo) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (appointmentId != null) {
            AppointmentBookingDetailModel detail = this.loadOutpatientBookingById(appointmentId);
            this.assertHealthCheckupBooking(detail, organizationId);
            return detail;
        }
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 与 appointmentBusNo 至少传一个");
        }
        // 按业务单号反查体检预约
        Specification<Appointment> specification = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            cb.equal(root.get("appointmentKind"), AppointmentKindConstants.HEALTH_CHECKUP_BOOKING),
            cb.equal(cb.lower(root.get("busNo")), busNo.trim().toLowerCase()));
        List<Appointment> rows = appointmentRepository.findAll(specification,
            Sort.by(Sort.Direction.DESC, FieldNameConstants.ID));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "体检预约不存在");
        }
        AppointmentBookingDetailModel detail = this.toBookingDetailModel(rows.get(0));
        this.assertHealthCheckupBooking(detail, organizationId);
        return detail;
    }

    /**
     * 重复预约校验：同日、同患者、同医疗服务（经 slot → schedule 解析）。
     *
     * @param patientId 患者标识
     * @param bookingDate 预约日期
     * @param healthcareServiceId 目标医疗服务标识
     * @param excludeAppointmentId 排除的预约主键（改约场景可传当前预约 id）
     * @return 已存在的有效预约详情；无重复则 empty
     */
    public Optional<AppointmentBookingDetailModel> findDuplicateOutpatientBooking(
        Long patientId, LocalDate bookingDate, Long healthcareServiceId, Long excludeAppointmentId) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "patientId 不能为空");
        }
        if (bookingDate == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "bookingDate 不能为空");
        }
        if (healthcareServiceId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "healthcareServiceId 不能为空");
        }

        LocalDateTime from = bookingDate.atStartOfDay();
        LocalDateTime toExclusive = bookingDate.plusDays(1).atStartOfDay();
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get("appointmentKind"), OUTPATIENT_BOOKING_KIND));
            predicates.add(cb.equal(root.get("patientId"), patientId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startInstant"), from));
            predicates.add(cb.lessThan(root.get("startInstant"), toExclusive));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Appointment> candidates = appointmentRepository.findAll(specification,
            Sort.by(Sort.Direction.ASC, "startInstant"));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> slotIds = new HashSet<>();
        for (Appointment appointment : candidates) {
            if (appointment.getSlotId() != null) {
                slotIds.add(appointment.getSlotId());
            }
        }
        if (slotIds.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, Long> slotToHealthcareServiceId = this.resolveHealthcareServiceIdBySlotIds(slotIds);
        for (Appointment appointment : candidates) {
            // 改约场景排除当前预约自身
            if (excludeAppointmentId != null && excludeAppointmentId.equals(appointment.getId())) {
                continue;
            }
            Long slotId = appointment.getSlotId();
            if (slotId == null) {
                continue;
            }
            Long resolvedServiceId = slotToHealthcareServiceId.get(slotId);
            // 同日同患者同医疗服务视为重复预约
            if (healthcareServiceId.equals(resolvedServiceId)) {
                return Optional.of(this.toBookingDetailModel(appointment));
            }
        }
        return Optional.empty();
    }

    /**
     * 按就诊查询有效预约摘要；无记录时返回 null。
     *
     * @param encounterId 就诊标识
     * @return 预约模型或 null
     */
    public AppointmentQueryModel findByEncounterId(Long encounterId) {
        Appointment appointment = appointmentRepository.findByEncounterId(encounterId).orElse(null);
        // 就诊尚未产生预约事实时返回空，由上层决定是否引导挂号/预约
        if (appointment == null) {
            return null;
        }
        AppointmentQueryModel model = new AppointmentQueryModel();
        model.setId(appointment.getId());
        model.setAppointmentKind(appointment.getAppointmentKind());
        model.setDisplayOrder(appointment.getDisplayOrder());
        model.setStatusView("ACTIVE");
        return model;
    }

    /**
     * 按主键加载有效候诊队列项（叫号/过号前快照）。
     *
     * @param appointmentId 候诊项主键
     * @return 队列行模型
     */
    public AppointmentQueuePageModel loadActiveQueueItemById(Long appointmentId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "候诊项不存在"));
        if (Boolean.TRUE.equals(appointment.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "候诊项已终态");
        }
        // 未报到入队则无 displayOrder，不可参与叫号
        if (appointment.getDisplayOrder() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "候诊项尚未报到入队");
        }
        return this.toQueuePageModel(appointment);
    }

    /**
     * 分页查询已完成候诊项（叫号/过号/取消等终态，或已叫号）。
     *
     * @param organizationId 机构主键
     * @param locationId 诊室主键
     * @param pageable 分页
     * @return 已完成队列分页
     */
    public Page<AppointmentQueuePageModel> pageCompletedQueue(
        Long organizationId, Long locationId, Pageable pageable) {
        if (organizationId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId/locationId 不能为空");
        }
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("destinationLocationId"), locationId));
            predicates.add(cb.isNotNull(root.get("displayOrder")));
            // 终态或已叫号均视为已完成候诊
            jakarta.persistence.criteria.Predicate terminal = cb.isTrue(root.get(FieldNameConstants.IS_DELETED));
            jakarta.persistence.criteria.Predicate called = cb.isNotNull(root.get("calledAt"));
            predicates.add(cb.or(terminal, called));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "calledAt", "cancellationAt", "displayOrder"));
        return appointmentRepository.findAll(specification, sorted).map(this::toQueuePageModel);
    }

    /**
     * 叫号大屏快照：当前叫号 + 等候队列（有效且已报到）。
     *
     * @param organizationId 机构主键
     * @param locationId 诊室主键
     * @return 按 displayOrder 升序的队列快照
     */
    public List<AppointmentQueuePageModel> listActiveQueueSnapshot(Long organizationId, Long locationId) {
        if (organizationId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId/locationId 不能为空");
        }
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("destinationLocationId"), locationId));
            predicates.add(cb.isNotNull(root.get("displayOrder")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Appointment> rows = appointmentRepository.findAll(specification,
            Sort.by(Sort.Direction.ASC, "displayOrder"));
        List<AppointmentQueuePageModel> models = new ArrayList<>();
        for (Appointment row : rows) {
            models.add(this.toQueuePageModel(row));
        }
        return models;
    }

    /**
     * 按机构与诊室分页查询候诊队列。
     *
     * @param organizationId 机构标识
     * @param locationId 诊室/目的地库位标识
     * @param pageable 分页
     * @return 分页队列行
     * @throws BusinessException 机构或诊室未传
     */
    public Page<AppointmentQueuePageModel> findQueueByOrganizationAndLocation(
        Long organizationId, Long locationId, Pageable pageable) {
        // 队列必须绑定到具体机构与诊室，否则无法界定候诊资源池
        if (organizationId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "查询队列时 organizationId/locationId 不能为空");
        }
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("destinationLocationId"), locationId));
            predicates.add(cb.isNotNull(root.get("displayOrder")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.ASC, "displayOrder"));
        return appointmentRepository.findAll(specification, sorted).map(this::toQueuePageModel);
    }

    /**
     * 批量按就诊查询预约摘要，保持入参顺序中有预约的就诊才输出一行。
     *
     * @param encounterIds 就诊 id 列表
     * @return 批量模型列表
     */
    public List<AppointmentBatchModel> findByEncounterIdIn(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Appointment> appointments = appointmentRepository.findByEncounterIdIn(encounterIds);
        Map<Long, Appointment> byEncounterId = new HashMap<>();
        for (Appointment appointment : appointments) {
            // 同一就诊多预约时保留首次命中，与入参顺序无关的稳定去重
            if (appointment.getEncounterId() != null && !byEncounterId.containsKey(appointment.getEncounterId())) {
                byEncounterId.put(appointment.getEncounterId(), appointment);
            }
        }

        List<AppointmentBatchModel> models = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            Appointment appointment = byEncounterId.get(encounterId);
            // 该就诊无预约记录则跳过，不造占位行
            if (appointment == null) {
                continue;
            }
            AppointmentBatchModel model = new AppointmentBatchModel();
            model.setEncounterId(encounterId);
            model.setAppointmentId(appointment.getId());
            model.setQueueStatus("ACTIVE");
            models.add(model);
        }
        return models;
    }

    /**
     * 按患者查询其名下预约时段列表（复诊、随访场景）。
     *
     * @param patientId 患者标识
     * @return 预约列表
     * @throws BusinessException patientId 为空
     */
    public List<PatientAppointmentModel> findByPatientId(Long patientId) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "按患者查询时 patientId 不能为空");
        }
        List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
        List<PatientAppointmentModel> models = new ArrayList<>();
        for (Appointment appointment : appointments) {
            PatientAppointmentModel model = new PatientAppointmentModel();
            model.setId(appointment.getId());
            model.setPatientId(appointment.getPatientId());
            model.setSlotId(appointment.getSlotId());
            model.setStartInstant(appointment.getStartInstant());
            models.add(model);
        }
        return models;
    }

    private AppointmentBookingListModel toBookingListModel(Appointment appointment) {
        AppointmentBookingListModel model = new AppointmentBookingListModel();
        model.setId(appointment.getId());
        model.setBusNo(appointment.getBusNo());
        model.setAppointmentKind(appointment.getAppointmentKind());
        model.setAppointmentTypeCode(appointment.getAppointmentTypeCode());
        model.setPatientId(appointment.getPatientId());
        model.setOrganizationId(appointment.getOrganizationId());
        model.setSlotId(appointment.getSlotId());
        model.setStartInstant(appointment.getStartInstant());
        model.setEndInstant(appointment.getEndInstant());
        model.setDestinationLocationId(appointment.getDestinationLocationId());
        model.setStatusView("ACTIVE");
        return model;
    }

    private void assertHealthCheckupBooking(AppointmentBookingDetailModel detail, Long organizationId) {
        if (detail == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "体检预约不存在");
        }
        if (!AppointmentKindConstants.HEALTH_CHECKUP_BOOKING.equals(detail.getAppointmentKind())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "非体检预约不可报到");
        }
        if (!organizationId.equals(detail.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约与机构不匹配");
        }
    }

    private AppointmentBookingDetailModel toBookingDetailModel(Appointment appointment) {
        AppointmentBookingDetailModel model = new AppointmentBookingDetailModel();
        model.setId(appointment.getId());
        model.setBusNo(appointment.getBusNo());
        model.setAppointmentKind(appointment.getAppointmentKind());
        model.setPatientId(appointment.getPatientId());
        model.setOrganizationId(appointment.getOrganizationId());
        model.setEncounterId(appointment.getEncounterId());
        model.setSlotId(appointment.getSlotId());
        model.setStartInstant(appointment.getStartInstant());
        model.setEndInstant(appointment.getEndInstant());
        model.setDestinationLocationId(appointment.getDestinationLocationId());
        model.setAppointmentTypeCode(appointment.getAppointmentTypeCode());
        model.setDisplayOrder(appointment.getDisplayOrder());
        model.setStatusView("ACTIVE");
        model.setCancelReason(appointment.getCancelReason());
        model.setCancellationAt(appointment.getCancellationAt());
        model.setCalledAt(appointment.getCalledAt());
        if (appointment.getOrigin() != null) {
            model.setOriginChannel(appointment.getOrigin().getOriginChannel());
        }
        return model;
    }

    /**
     * 按号源集合加载有效门诊预约（停诊影响扫描）。
     *
     * @param slotIds 号源主键集合
     * @return 预约详情列表
     */
    public List<AppointmentBookingDetailModel> listActiveOutpatientBookingsBySlotIds(List<Long> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> distinctSlotIds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long slotId : slotIds) {
            if (slotId != null && !seen.contains(slotId)) {
                seen.add(slotId);
                distinctSlotIds.add(slotId);
            }
        }
        if (distinctSlotIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Appointment> rows = appointmentRepository.findBySlotIdInAndIsDeletedFalse(distinctSlotIds);
        List<AppointmentBookingDetailModel> models = new ArrayList<>();
        for (Appointment appointment : rows) {
            // 停诊扫描仅关心门诊预约形态
            if (!OUTPATIENT_BOOKING_KIND.equals(appointment.getAppointmentKind())) {
                continue;
            }
            models.add(this.toBookingDetailModel(appointment));
        }
        return models;
    }

    /**
     * 统计自然月内有效门诊预约数（月历摘要，2.23）。
     *
     * @param organizationId 科室主键
     * @param rangeStart 区间起（含）
     * @param rangeEnd 区间止（含）
     * @return 预约条数
     */
    public int countActiveOutpatientBookingsInRange(
        Long organizationId, LocalDate rangeStart, LocalDate rangeEnd) {
        if (organizationId == null || rangeStart == null || rangeEnd == null) {
            return 0;
        }
        // 非法区间直接返回 0，避免误统计
        if (rangeEnd.isBefore(rangeStart)) {
            return 0;
        }
        LocalDateTime from = rangeStart.atStartOfDay();
        LocalDateTime toExclusive = rangeEnd.plusDays(1).atStartOfDay();
        Specification<Appointment> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("appointmentKind"), OUTPATIENT_BOOKING_KIND));
            predicates.add(cb.greaterThanOrEqualTo(root.get("startInstant"), from));
            predicates.add(cb.lessThan(root.get("startInstant"), toExclusive));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return (int) appointmentRepository.count(specification);
    }

    /**
     * 将预约实体映射为队列展示行。
     *
     * @param appointment 预约实体
     * @return 队列分页行模型
     */
    private AppointmentQueuePageModel toQueuePageModel(Appointment appointment) {
        AppointmentQueuePageModel model = new AppointmentQueuePageModel();
        model.setId(appointment.getId());
        model.setEncounterId(appointment.getEncounterId());
        model.setTriageLevel(appointment.getTriageLevel());
        model.setDisplayOrder(appointment.getDisplayOrder());
        model.setStatusView("ACTIVE");
        model.setBusNo(appointment.getBusNo());
        model.setPatientId(appointment.getPatientId());
        model.setAppointmentKind(appointment.getAppointmentKind());
        model.setStartInstant(appointment.getStartInstant());
        model.setCalledAt(appointment.getCalledAt());
        model.setSlotId(appointment.getSlotId());
        return model;
    }

    private Map<Long, Long> resolveHealthcareServiceIdBySlotIds(Set<Long> slotIds) {
        List<Slot> slots = slotRepository.findAllById(slotIds);
        Set<Long> scheduleIds = new HashSet<>();
        Map<Long, Long> slotToScheduleId = new HashMap<>();
        for (Slot slot : slots) {
            if (Boolean.TRUE.equals(slot.getIsDeleted())) {
                continue;
            }
            if (slot.getScheduleId() != null) {
                scheduleIds.add(slot.getScheduleId());
                slotToScheduleId.put(slot.getId(), slot.getScheduleId());
            }
        }
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> scheduleToServiceId = new HashMap<>();
        for (Schedule schedule : scheduleRepository.findAllById(scheduleIds)) {
            if (Boolean.TRUE.equals(schedule.getIsDeleted())) {
                continue;
            }
            scheduleToServiceId.put(schedule.getId(), schedule.getHealthcareServiceId());
        }

        Map<Long, Long> slotToServiceId = new HashMap<>();
        for (Map.Entry<Long, Long> entry : slotToScheduleId.entrySet()) {
            Long serviceId = scheduleToServiceId.get(entry.getValue());
            if (serviceId != null) {
                slotToServiceId.put(entry.getKey(), serviceId);
            }
        }
        return slotToServiceId;
    }
}


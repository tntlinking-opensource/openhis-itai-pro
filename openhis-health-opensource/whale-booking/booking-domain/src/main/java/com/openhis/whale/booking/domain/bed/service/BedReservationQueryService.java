package com.openhis.whale.booking.domain.bed.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import com.openhis.whale.booking.domain.bed.repository.BedReservationExtRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BedReservationQueryService {

    private final BedReservationExtRepository repository;

    public Optional<BedReservationExt> findByAppointmentId(Long appointmentId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        return repository.findByAppointmentIdAndIsDeletedFalse(appointmentId);
    }

    public List<BedReservationExt> findByPatientId(Long patientId) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "patientId 不能为空");
        }
        return repository.findByPatientIdAndIsDeletedFalse(patientId);
    }

    public Page<BedReservationExt> pageReservations(Long wardLocationId, Pageable pageable) {
        return this.pageReservations(null, wardLocationId, null, null, pageable);
    }

    /**
     * 床位预留扩展分页（可按病区与意向入院日收窄）。
     */
    public Page<BedReservationExt> pageReservations(
        Long organizationId,
        Long wardLocationId,
        java.time.LocalDate dateFrom,
        java.time.LocalDate dateTo,
        Pageable pageable) {
        Specification<BedReservationExt> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (wardLocationId != null) {
                p.getExpressions().add(cb.equal(root.get("wardLocationId"), wardLocationId));
            }
            if (dateFrom != null) {
                p.getExpressions().add(cb.greaterThanOrEqualTo(root.get("intendedAdmissionDate"), dateFrom));
            }
            if (dateTo != null) {
                p.getExpressions().add(cb.lessThanOrEqualTo(root.get("intendedAdmissionDate"), dateTo));
            }
            return p;
        };
        return repository.findAll(spec, pageable);
    }

    public List<BedReservationExt> listByWardAndDateRange(Long wardLocationId, java.time.LocalDate from, java.time.LocalDate to) {
        Specification<BedReservationExt> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (wardLocationId != null) {
                p.getExpressions().add(cb.equal(root.get("wardLocationId"), wardLocationId));
            }
            if (from != null) {
                p.getExpressions().add(cb.greaterThanOrEqualTo(root.get("intendedAdmissionDate"), from));
            }
            if (to != null) {
                p.getExpressions().add(cb.lessThanOrEqualTo(root.get("intendedAdmissionDate"), to));
            }
            return p;
        };
        return repository.findAll(spec);
    }

    public BedReservationExt loadById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "id 不能为空");
        }
        BedReservationExt ext = repository.findById(id).orElse(null);
        if (ext == null || Boolean.TRUE.equals(ext.getIsDeleted())) {
            return null;
        }
        return ext;
    }
}

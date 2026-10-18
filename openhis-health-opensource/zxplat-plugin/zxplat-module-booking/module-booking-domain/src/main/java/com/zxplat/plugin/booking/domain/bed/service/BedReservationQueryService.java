/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.bed.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.bed.entity.BedReservationExt;
import com.zxplat.plugin.booking.domain.bed.repository.BedReservationExtRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 床位预约扩展读侧查询。
 */
@Service
@RequiredArgsConstructor
public class BedReservationQueryService {

    private final BedReservationExtRepository repository;

    /**
     * 按预约主键查有效床位扩展。
     *
     * @param appointmentId 预约主键
     * @return 扩展；无则 empty
     * @throws BusinessException appointmentId 为空时
     */
    public Optional<BedReservationExt> findByAppointmentId(Long appointmentId) {
        if (appointmentId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        return repository.findByAppointmentIdAndIsDeletedFalse(appointmentId);
    }

    /**
     * 按患者查其名下有效床位预约扩展。
     *
     * @param patientId 患者主键
     * @return 扩展列表
     * @throws BusinessException patientId 为空时
     */
    public List<BedReservationExt> findByPatientId(Long patientId) {
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "patientId 不能为空");
        }
        return repository.findByPatientIdAndIsDeletedFalse(patientId);
    }

    /**
     * 按床位查有效预留扩展。
     *
     * @param locationId 床位 Location 主键
     * @return 扩展列表
     */
    public List<BedReservationExt> findActiveByLocationId(Long locationId) {
        if (locationId == null) {
            return List.of();
        }
        return repository.findByLocationIdAndIsDeletedFalse(locationId);
    }

    /**
     * 按病区分页查询床位预留（兼容旧入参）。
     *
     * @param wardLocationId 病区库位主键
     * @param pageable 分页
     * @return 扩展分页
     */
    public Page<BedReservationExt> pageReservations(Long wardLocationId, Pageable pageable) {
        return this.pageReservations(null, wardLocationId, null, null, pageable);
    }

    /**
     * 床位预留扩展分页（可按病区与意向入院日收窄）。
     *
     * @param organizationId 机构主键（预留，当前未参与筛选）
     * @param wardLocationId 病区库位主键
     * @param dateFrom 意向入院日起
     * @param dateTo 意向入院日止
     * @param pageable 分页
     * @return 扩展分页
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

    /**
     * 按病区与意向入院日区间列出有效预留。
     *
     * @param wardLocationId 病区库位主键
     * @param from 区间起
     * @param to 区间止
     * @return 扩展列表
     */
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

    /**
     * 按主键加载有效床位扩展。
     *
     * @param id 扩展主键
     * @return 有效扩展或 null
     * @throws BusinessException id 为空时
     */
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

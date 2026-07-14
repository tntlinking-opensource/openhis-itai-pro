/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.bed.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import com.openhis.whale.booking.domain.bed.repository.BedReservationExtRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 床位预约扩展写侧。
 *
 * <p>与 {@code Appointment} 床位形态配对，承载病区、意向入院日等扩展字段。</p>
 */
@Service
@RequiredArgsConstructor
public class BedReservationDomainService {

    private final BedReservationExtRepository repository;

    /**
     * 保存床位预约扩展。
     *
     * @param entity 扩展实体
     * @param operatorId 操作人
     * @return 落库后的实体
     * @throws BusinessException 预约或床位未绑定时
     */
    public BedReservationExt save(BedReservationExt entity, Long operatorId) {
        // 前置校验：扩展须挂靠有效预约与床位
        if (entity.getAppointmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "appointmentId 不能为空");
        }
        if (entity.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "locationId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        // 持久化
        return repository.save(entity);
    }

    /**
     * 软删床位预约扩展。
     *
     * @param id 扩展主键
     * @param operatorId 操作人
     */
    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}

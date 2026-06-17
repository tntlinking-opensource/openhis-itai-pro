package com.openhis.whale.booking.domain.bed.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.bed.entity.BedReservationExt;
import com.openhis.whale.booking.domain.bed.repository.BedReservationExtRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BedReservationDomainService {

    private final BedReservationExtRepository repository;

    public BedReservationExt save(BedReservationExt entity, Long operatorId) {
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
        return repository.save(entity);
    }

    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}

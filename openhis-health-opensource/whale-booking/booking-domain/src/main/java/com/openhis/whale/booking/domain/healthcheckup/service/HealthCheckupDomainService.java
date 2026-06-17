package com.openhis.whale.booking.domain.healthcheckup.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckBookingExt;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackageItem;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupBatchRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupOrgRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupRosterRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.HealthCheckBookingExtRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.HealthCheckPackageItemRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.HealthCheckPackageRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthCheckupDomainService {

    private final GroupCheckupOrgRepository orgRepository;
    private final GroupCheckupBatchRepository batchRepository;
    private final GroupCheckupRosterRepository rosterRepository;
    private final HealthCheckPackageRepository packageRepository;
    private final HealthCheckPackageItemRepository packageItemRepository;
    private final HealthCheckBookingExtRepository bookingExtRepository;

    public GroupCheckupOrg saveOrg(GroupCheckupOrg entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, orgRepository::save);
    }

    public void deleteOrg(Long id, Long operatorId) {
        this.softDelete(id, operatorId, orgRepository);
    }

    public GroupCheckupBatch saveBatch(GroupCheckupBatch entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, batchRepository::save);
    }

    public void deleteBatch(Long id, Long operatorId) {
        this.softDelete(id, operatorId, batchRepository);
    }

    public GroupCheckupRoster saveRoster(GroupCheckupRoster entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, rosterRepository::save);
    }

    public void deleteRoster(Long id, Long operatorId) {
        this.softDelete(id, operatorId, rosterRepository);
    }

    public HealthCheckPackage savePackage(HealthCheckPackage entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, packageRepository::save);
    }

    public void deletePackage(Long id, Long operatorId) {
        this.softDelete(id, operatorId, packageRepository);
    }

    public HealthCheckPackageItem savePackageItem(HealthCheckPackageItem entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, packageItemRepository::save);
    }

    public void deletePackageItem(Long id, Long operatorId) {
        this.softDelete(id, operatorId, packageItemRepository);
    }

    public void replacePackageItems(Long packageId, List<HealthCheckPackageItem> items, Long operatorId) {
        if (packageId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "packageId 不能为空");
        }
        List<HealthCheckPackageItem> existing = packageItemRepository.findByPackageIdAndIsDeletedFalseOrderBySortOrderAsc(packageId);
        for (HealthCheckPackageItem row : existing) {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(LocalDateTime.now());
            row.setDeletedBy(operatorId);
        }
        if (!existing.isEmpty()) {
            packageItemRepository.saveAll(existing);
        }
        if (items != null) {
            for (HealthCheckPackageItem item : items) {
                item.setPackageId(packageId);
                item.setId(null);
                this.savePackageItem(item, operatorId);
            }
        }
    }

    public HealthCheckBookingExt saveBookingExt(HealthCheckBookingExt entity, Long operatorId) {
        return this.saveAudited(entity, operatorId, bookingExtRepository::save);
    }

    public void incrementBatchBookedCount(Long batchId, int delta) {
        if (batchId == null || delta == 0) {
            return;
        }
        GroupCheckupBatch batch = batchRepository.findById(batchId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, "团检批次不存在"));
        int next = batch.getBookedCount() + delta;
        if (next < 0) {
            next = 0;
        }
        if (batch.getCapacityLimit() != null && batch.getCapacityLimit() > 0 && next > batch.getCapacityLimit()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_RULE_CONFLICT, "团检批次容量已满");
        }
        batch.setBookedCount(next);
        batchRepository.save(batch);
    }

    private <T extends com.openhis.whale.core.domain.BaseAuditedEntity> T saveAudited(
        T entity, Long operatorId, java.util.function.Function<T, T> saver) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return saver.apply(entity);
    }

    private <T extends com.openhis.whale.core.domain.BaseAuditedEntity> void softDelete(
        Long id, Long operatorId, org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
        repository.findById(id).ifPresent(entity -> {
            entity.setIsDeleted(Boolean.TRUE);
            entity.setDeletedAt(LocalDateTime.now());
            entity.setDeletedBy(operatorId);
            repository.save(entity);
        });
    }
}

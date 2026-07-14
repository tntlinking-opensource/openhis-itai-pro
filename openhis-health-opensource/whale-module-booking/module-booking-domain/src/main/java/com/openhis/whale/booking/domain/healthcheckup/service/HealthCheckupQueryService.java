/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.healthcheckup.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupBatch;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupOrg;
import com.openhis.whale.booking.domain.healthcheckup.entity.GroupCheckupRoster;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackage;
import com.openhis.whale.booking.domain.healthcheckup.entity.HealthCheckPackageItem;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupBatchRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupOrgRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.GroupCheckupRosterRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.HealthCheckPackageItemRepository;
import com.openhis.whale.booking.domain.healthcheckup.repository.HealthCheckPackageRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthCheckupQueryService {

    private final GroupCheckupOrgRepository orgRepository;
    private final GroupCheckupBatchRepository batchRepository;
    private final GroupCheckupRosterRepository rosterRepository;
    private final HealthCheckPackageRepository packageRepository;
    private final HealthCheckPackageItemRepository packageItemRepository;

    public Page<GroupCheckupOrg> pageOrgs(String keyword, Pageable pageable) {
        Specification<GroupCheckupOrg> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                p.getExpressions().add(cb.or(
                    cb.like(cb.lower(root.get("orgCode")), like),
                    cb.like(cb.lower(root.get("orgName")), like)));
            }
            return p;
        };
        return orgRepository.findAll(spec, pageable);
    }

    public GroupCheckupOrg loadOrgById(Long id) {
        return this.loadActive(orgRepository.findById(id).orElse(null), "团检单位不存在");
    }

    public Page<GroupCheckupBatch> pageBatches(Long orgId, Pageable pageable) {
        Specification<GroupCheckupBatch> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (orgId != null) {
                p.getExpressions().add(cb.equal(root.get("orgId"), orgId));
            }
            return p;
        };
        return batchRepository.findAll(spec, pageable);
    }

    public GroupCheckupBatch loadBatchById(Long id) {
        return this.loadActive(batchRepository.findById(id).orElse(null), "团检批次不存在");
    }

    public Page<GroupCheckupRoster> pageRoster(Long batchId, Pageable pageable) {
        if (batchId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "batchId 不能为空");
        }
        Specification<GroupCheckupRoster> spec = (root, query, cb) -> cb.and(
            cb.equal(root.get("isDeleted"), false),
            cb.equal(root.get("batchId"), batchId));
        return rosterRepository.findAll(spec, pageable);
    }

    public List<GroupCheckupRoster> listPendingRosterByBatch(Long batchId) {
        return rosterRepository.findByBatchIdAndBookingStatusCodeAndIsDeletedFalse(batchId, "PENDING");
    }

    public Page<HealthCheckPackage> pagePackages(Long organizationId, Pageable pageable) {
        Specification<HealthCheckPackage> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            p.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (organizationId != null) {
                p.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return p;
        };
        return packageRepository.findAll(spec, pageable);
    }

    public HealthCheckPackage loadPackageById(Long id) {
        return this.loadActive(packageRepository.findById(id).orElse(null), "体检套餐不存在");
    }

    public List<HealthCheckPackageItem> listPackageItems(Long packageId) {
        return packageItemRepository.findByPackageIdAndIsDeletedFalseOrderBySortOrderAsc(packageId);
    }

    private <T> T loadActive(T entity, String message) {
        if (entity == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, message);
        }
        if (entity instanceof com.openhis.whale.core.domain.BaseAuditedEntity audited
            && Boolean.TRUE.equals(audited.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_NOT_FOUND, message);
        }
        return entity;
    }
}

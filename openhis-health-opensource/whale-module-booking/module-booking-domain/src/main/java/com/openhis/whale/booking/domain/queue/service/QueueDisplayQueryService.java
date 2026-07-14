/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.queue.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.queue.entity.QueueDisplayConfig;
import com.openhis.whale.booking.domain.queue.repository.QueueDisplayConfigRepository;
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
public class QueueDisplayQueryService {

    private final QueueDisplayConfigRepository repository;

    public Page<QueueDisplayConfig> pageByOrganization(Long organizationId, Pageable pageable) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        Specification<QueueDisplayConfig> spec = (root, query, cb) -> cb.and(
            cb.equal(root.get("isDeleted"), false),
            cb.equal(root.get("organizationId"), organizationId));
        return repository.findAll(spec, pageable);
    }

    public List<QueueDisplayConfig> listByOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        return repository.findByOrganizationIdAndIsDeletedFalse(organizationId);
    }

    public Optional<QueueDisplayConfig> findByOrganizationAndLocation(Long organizationId, Long locationId) {
        return repository.findByOrganizationIdAndLocationIdAndIsDeletedFalse(organizationId, locationId);
    }

    public QueueDisplayConfig loadById(Long id) {
        QueueDisplayConfig config = repository.findById(id).orElse(null);
        if (config == null || Boolean.TRUE.equals(config.getIsDeleted())) {
            return null;
        }
        return config;
    }
}

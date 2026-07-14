/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.borrowrequest.repository;

import com.openhis.health.domain.mrms.borrowrequest.entity.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 借阅申请仓储。
 */
public interface BorrowRequestRepository
        extends JpaRepository<BorrowRequest, Long>, JpaSpecificationExecutor<BorrowRequest> {

    /**
     * 按主键加载未软删借阅申请。
     *
     * @param id 借阅申请主键
     * @return 存在则含实体，否则 empty
     */
    Optional<BorrowRequest> findByIdAndIsDeletedFalse(Long id);
}

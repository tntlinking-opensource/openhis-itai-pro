/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUploadTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 进销存上报追溯码明细仓储。
 */
public interface YbInvUploadTraceRepository extends JpaRepository<YbInvUploadTrace, Long> {

    /**
     * 按上报流水主键查询追溯码明细，按 id 升序。
     *
     * @param uploadId 上报流水主键
     * @return 追溯码明细列表
     */
    List<YbInvUploadTrace> findAllByUploadIdOrderByIdAsc(Long uploadId);
}

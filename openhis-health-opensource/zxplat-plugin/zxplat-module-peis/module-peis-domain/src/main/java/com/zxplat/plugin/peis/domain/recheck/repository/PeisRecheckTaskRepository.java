/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.recheck.repository;

import com.zxplat.plugin.peis.domain.recheck.entity.PeisRecheckTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 复查任务仓储。
 */
public interface PeisRecheckTaskRepository
    extends JpaRepository<PeisRecheckTask, Long>, JpaSpecificationExecutor<PeisRecheckTask> {

    /**
     * 按就诊列出未软删复查任务。
     *
     * @param encounterId 就诊主键
     * @return 任务列表
     */
    List<PeisRecheckTask> findByEncounterIdAndIsDeletedFalse(Long encounterId);

    /**
     * 是否存在未关闭复查任务。
     *
     * @param encounterId  就诊主键
     * @param statusEnums  开放状态集合
     * @return 是否存在
     */
    boolean existsByEncounterIdAndStatusEnumInAndIsDeletedFalse(Long encounterId, List<String> statusEnums);
}

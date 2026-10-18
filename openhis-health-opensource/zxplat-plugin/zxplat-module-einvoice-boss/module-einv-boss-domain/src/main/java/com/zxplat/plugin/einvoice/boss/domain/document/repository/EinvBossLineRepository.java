/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.document.repository;

import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 博思报文明细行仓储（{@code einv_boss_line}）。
 *
 * <p>出站组装前按单据主键加载明细，保证 lineNo 升序与持久化一致。</p>
 */
public interface EinvBossLineRepository extends JpaRepository<EinvBossLine, Long> {

    /**
     * 按单据主键查询未删除明细行，按行号升序。
     *
     * @param documentId 单据主键
     * @return 明细行列表
     */
    List<EinvBossLine> findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(Long documentId);
}

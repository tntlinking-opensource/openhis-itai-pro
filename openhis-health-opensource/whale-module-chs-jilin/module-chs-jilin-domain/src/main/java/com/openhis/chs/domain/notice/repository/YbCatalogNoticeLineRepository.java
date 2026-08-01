/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.repository;

import com.openhis.chs.domain.notice.entity.YbCatalogNoticeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 医保文件通知明细行仓储；仅本聚合访问。
 */
public interface YbCatalogNoticeLineRepository
    extends JpaRepository<YbCatalogNoticeLine, Long>, JpaSpecificationExecutor<YbCatalogNoticeLine> {

    /**
     * 按文件头与行状态取未删除明细（应用时取 ACTIVE）。
     *
     * @param docId      文件头主键
     * @param lineStatus 行状态
     * @return 明细列表
     */
    List<YbCatalogNoticeLine> findByDocIdAndLineStatusAndIsDeletedFalse(Long docId, String lineStatus);

    /**
     * 待应用行主键（尚未写出 apply_result），仅投影 id，避免大包全量实体加载。
     */
    @Query("""
        select l.id from YbCatalogNoticeLine l
        where l.docId = :docId
          and l.lineStatus = :lineStatus
          and l.isDeleted = false
          and (l.applyResult is null or trim(l.applyResult) = '')
        order by l.id asc
        """)
    List<Long> findPendingApplyLineIds(
        @Param("docId") Long docId,
        @Param("lineStatus") String lineStatus);

    /**
     * 待应用行数（续跑/进度分母）。
     */
    @Query("""
        select count(l.id) from YbCatalogNoticeLine l
        where l.docId = :docId
          and l.lineStatus = :lineStatus
          and l.isDeleted = false
          and (l.applyResult is null or trim(l.applyResult) = '')
        """)
    long countPendingApplyLines(
        @Param("docId") Long docId,
        @Param("lineStatus") String lineStatus);
}

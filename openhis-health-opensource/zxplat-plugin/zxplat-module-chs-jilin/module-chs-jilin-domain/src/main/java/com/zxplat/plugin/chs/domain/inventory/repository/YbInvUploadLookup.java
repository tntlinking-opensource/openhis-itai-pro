/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.repository;

import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 进销存上报流水复杂只读查询（JdbcTemplate；Repository 接口禁 {@code @Query}） */
public interface YbInvUploadLookup {

    /**
     * 查询可重试的上报流水：失败/待上报且未超最大重试次数、已到重试时间。
     *
     * @param tenantId 租户
     * @param statuses 可重试状态列表
     * @param maxRetry 最大重试次数（不含）
     * @param now 当前时间
     * @param pageable 批次分页
     * @return 待重试流水，按 next_retry_at、id 升序 */
    List<YbInvUpload> findRetryable(
        Long tenantId,
        List<String> statuses,
        int maxRetry,
        LocalDateTime now,
        Pageable pageable
    );

    /**
     * 运维本地流水多条件 AND 筛选（契约/交易号/来源/状态/批次号/创建时间均可空）。
     *
     * @param tenantId       租户主键
     * @param contractBusNo  契约业务编号；空则不限
     * @param infno          交易号；空则不限
     * @param sourceType     来源类型；空则不限
     * @param sourceId       来源主键；空则不限
     * @param uploadStatus   上报状态；空则不限；SKIPPED 前缀匹配
     * @param fixmedinsBchno 批次流水号；空则不限；模糊 LIKE
     * @param createdFrom    创建时间起（含）；空则不限
     * @param createdTo      创建时间止（含）；空则不限
     * @param pageable       分页（limit/offset）
     * @return 按 id 降序的流水列表
     */
    List<YbInvUpload> findLocalRecords(
        Long tenantId,
        String contractBusNo,
        String infno,
        String sourceType,
        Long sourceId,
        String uploadStatus,
        String fixmedinsBchno,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Pageable pageable
    );

    /**
     * 报文中是否至少有一条 {@code drug_trac_codg} 存在于本租户 {@code cus_trace_number} 主档。
     *
     * <p>用于区分「191 + 主档无码（历史挂账可隐藏）」与「191 + 主档有码（院内入过库须处理）」。</p>
     *
     * @param tenantId    租户主键
     * @param requestJson 持久化 request_json；可空
     * @return 任一码在主档为 true；无码/无主档命中为 false
     */
    boolean hasAnyRequestDrugTracInMaster(Long tenantId, String requestJson);

    /**
     * 按追溯码查找最近一次「入库类」成功 3502 的出站契约 bus_no（市入→市、省入→省）。
     *
     * <p>含已软删的 {@code yb_inv_upload_trace} 历史挂载（同码再写会软删旧归属）。
     * 入库类 {@code inv_chg_type}：101/103/106/108/110。</p>
     *
     * @param tenantId       租户主键
     * @param drugTracCodgs  本片追溯码；空则返回 null
     * @return 契约 bus_no；未命中 null
     */
    String findLatestInboundStockContractBusNo(Long tenantId, List<String> drugTracCodgs);
}

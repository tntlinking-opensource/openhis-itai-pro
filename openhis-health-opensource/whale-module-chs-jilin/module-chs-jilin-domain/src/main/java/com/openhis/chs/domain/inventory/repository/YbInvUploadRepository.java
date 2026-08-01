/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.repository;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 进销存上报流水仓储。
 */
public interface YbInvUploadRepository extends JpaRepository<YbInvUpload, Long> {

    /**
     * 按租户、接口编号、定点批次流水号与来源行主键查询首条上报流水。
     *
     * @param tenantId       租户主键
     * @param infno          医保接口编号
     * @param fixmedinsBchno 定点医药机构批次流水号
     * @param sourceLineId   来源业务行主键
     * @return 匹配的上报流水，未找到时 empty
     */
    Optional<YbInvUpload> findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
        Long tenantId,
        String infno,
        String fixmedinsBchno,
        Long sourceLineId
    );

    /**
     * 按租户、接口编号、批次号、来源类型与上报状态查最近一条（清床冲销标删配对 3505）。
     *
     * @param tenantId       租户主键
     * @param infno          医保接口编号
     * @param fixmedinsBchno 定点医药机构批次流水号（原清床 3505 批次，非退药单号）
     * @param sourceType     来源业务类型
     * @param uploadStatus   上报状态
     * @return 最近一条匹配流水，未找到时 empty
     */
    Optional<YbInvUpload> findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceTypeAndUploadStatusOrderByIdDesc(
        Long tenantId,
        String infno,
        String fixmedinsBchno,
        String sourceType,
        String uploadStatus
    );

    /**
     * 按租户、接口编号、来源行主键与上报状态查询最近一条上报流水。
     *
     * @param tenantId     租户主键
     * @param infno        医保接口编号
     * @param sourceLineId 来源业务行主键
     * @param uploadStatus 上报状态
     * @return 最近一条匹配的上报流水，未找到时 empty
     */
    Optional<YbInvUpload> findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
        Long tenantId,
        String infno,
        Long sourceLineId,
        String uploadStatus
    );

    /**
     * 按租户、来源类型与来源主键查询上报流水，按 id 升序。
     *
     * @param tenantId   租户主键
     * @param sourceType 来源业务类型
     * @param sourceId   来源业务主键
     * @return 上报流水列表
     */
    List<YbInvUpload> findAllByTenantIdAndSourceTypeAndSourceIdOrderByIdAsc(
        Long tenantId,
        String sourceType,
        Long sourceId
    );

    /**
     * 按租户与接口编号查询上报流水，按 id 降序并分页。
     *
     * @param tenantId 租户主键
     * @param infno    医保接口编号
     * @param pageable 分页参数
     * @return 上报流水列表
     */
    List<YbInvUpload> findAllByTenantIdAndInfnoOrderByIdDesc(
        Long tenantId,
        String infno,
        Pageable pageable
    );

    /**
     * 按租户查询上报流水，按 id 降序并分页。
     *
     * @param tenantId 租户主键
     * @param pageable 分页参数
     * @return 上报流水列表
     */
    List<YbInvUpload> findAllByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    /**
     * 按租户与定点批次流水号查询上报流水，按 id 降序并分页。
     *
     * @param tenantId       租户主键
     * @param fixmedinsBchno 定点医药机构批次流水号
     * @param pageable       分页参数
     * @return 上报流水列表
     */
    List<YbInvUpload> findAllByTenantIdAndFixmedinsBchnoOrderByIdDesc(
        Long tenantId,
        String fixmedinsBchno,
        Pageable pageable
    );

    /**
     * 按租户与上报状态查询上报流水，按 id 降序并分页。
     *
     * @param tenantId     租户主键
     * @param uploadStatus 上报状态
     * @param pageable     分页参数
     * @return 上报流水列表
     */
    List<YbInvUpload> findAllByTenantIdAndUploadStatusOrderByIdDesc(
        Long tenantId,
        String uploadStatus,
        Pageable pageable
    );
}

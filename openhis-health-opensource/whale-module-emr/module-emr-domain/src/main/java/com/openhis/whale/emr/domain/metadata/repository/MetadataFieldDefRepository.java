/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.metadata.repository;

import com.openhis.whale.emr.domain.metadata.entity.MetadataFieldDef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 元数据字段定义仓储。表 emr_metadata_field_def；维护页分页走 Specification。
 */
@Repository
public interface MetadataFieldDefRepository
        extends JpaRepository<MetadataFieldDef, Long>, JpaSpecificationExecutor<MetadataFieldDef> {

    /** 按主键查未软删字段定义。 */
    Optional<MetadataFieldDef> findByIdAndIsDeletedFalse(Long id);

    /** 按 fieldKey 查未软删定义（唯一性校验）。 */
    Optional<MetadataFieldDef> findByFieldKeyAndIsDeletedFalse(String fieldKey);

    /** 新建时 fieldKey 全局唯一（未软删）。 */
    boolean existsByFieldKeyAndIsDeletedFalse(String fieldKey);

    /** 更新时 fieldKey 唯一（排除自身）。 */
    boolean existsByFieldKeyAndIsDeletedFalseAndIdNot(String fieldKey, Long id);

    /** 租户内 fieldKey 唯一（新建）。 */
    boolean existsByFieldKeyAndIsDeletedFalseAndTenantId(String fieldKey, Long tenantId);

    /** 租户内 fieldKey 唯一（更新，排除自身）。 */
    boolean existsByFieldKeyAndIsDeletedFalseAndTenantIdAndIdNot(String fieldKey, Long tenantId, Long id);

    /** 全部启用字段，供下拉与元数据解析；条数随配置增长，须控制启用数量。 */
    List<MetadataFieldDef> findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc();
}

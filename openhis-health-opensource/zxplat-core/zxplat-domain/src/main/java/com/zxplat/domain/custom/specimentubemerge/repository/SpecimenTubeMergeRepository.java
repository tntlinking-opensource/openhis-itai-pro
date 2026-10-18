/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.repository;

import com.zxplat.domain.custom.specimentubemerge.entity.SpecimenTubeMerge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标本合管配置仓储（{@code cus_specimen_tube_merge}，仅本聚合；禁止 @Query）。
 */
public interface SpecimenTubeMergeRepository extends JpaRepository<SpecimenTubeMerge, Long> {

    /**
     * 列出某设备下未软删的合管绑定行。
     *
     * @param deviceId 设备主键
     * @return 绑定行列表
     */
    List<SpecimenTubeMerge> findByDeviceIdAndIsDeletedFalse(Long deviceId);

    /**
     * 列出某设备某合管组下未软删的绑定行。
     *
     * @param deviceId 设备主键
     * @param groupNo  组序号
     * @return 绑定行列表
     */
    List<SpecimenTubeMerge> findByDeviceIdAndGroupNoAndIsDeletedFalse(Long deviceId, Integer groupNo);

    /**
     * 判断目标是否已被其它设备占用（BR-STM-002：仅检验项目写侧全局唯一）。
     *
     * @param targetTable 目标表名
     * @param targetId    目标主键
     * @param deviceId    排除的设备主键
     * @return 是否存在冲突
     */
    boolean existsByTargetTableAndTargetIdAndIsDeletedFalseAndDeviceIdNot(
        String targetTable, Long targetId, Long deviceId);

    /**
     * 按设备、组与目标定位未软删行（对齐 upsert 用）。
     *
     * @param deviceId    设备主键
     * @param groupNo     组序号
     * @param targetTable 目标表名
     * @param targetId    目标主键
     * @return 绑定行
     */
    Optional<SpecimenTubeMerge> findByDeviceIdAndGroupNoAndTargetTableAndTargetIdAndIsDeletedFalse(
        Long deviceId, Integer groupNo, String targetTable, Long targetId);

    /**
     * 按绑定目标批量定位未软删行（activity 反查合管组用）。
     *
     * @param targetTable 目标表名
     * @param targetIds   目标主键集合
     * @return 绑定行列表
     */
    List<SpecimenTubeMerge> findByTargetTableAndTargetIdInAndIsDeletedFalse(
        String targetTable, Collection<Long> targetIds);

    /**
     * 按绑定目标定位未软删行。
     *
     * @param targetTable 目标表名
     * @param targetId    目标主键
     * @return 绑定行
     */
    Optional<SpecimenTubeMerge> findByTargetTableAndTargetIdAndIsDeletedFalse(
        String targetTable, Long targetId);
}

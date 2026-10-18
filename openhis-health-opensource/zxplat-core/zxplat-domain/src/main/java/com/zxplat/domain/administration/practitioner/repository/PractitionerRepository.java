/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.repository;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.framework.core.infrastructure.transaction.SkipSoftDeleteFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


/**
 * 医务人员仓储。
 */
public interface PractitionerRepository extends JpaRepository<Practitioner, Long>, JpaSpecificationExecutor<Practitioner> {

    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<Practitioner> findById(Long id);

    /**
     * 判断是否存在满足条件的记录。
     * @param userId 条件
     * @return 查询结果
     */
    boolean existsByUserId(Long userId);

    /**
     * 按绑定用户查询（含已软删记录），创建前校验用户是否已绑定医务人员。
     */
    @SkipSoftDeleteFilter
    Optional<Practitioner> findFirstByUserIdOrderByIdAsc(Long userId);

    /**
     * 按绑定用户查询医务人员主档（未逻辑删除），用户与主档通常一对一。
     *
     * @param userId 系统用户主键
     * @return 从业人员实体
     */
    Optional<Practitioner> findFirstByUserIdAndIsDeletedFalseOrderByIdAsc(Long userId);

    /**
     * 按绑定用户主键批量查询未删除的医务人员（预交金流水操作员展示）。
     *
     * @param userIds 系统用户主键集合
     * @return 从业人员列表
     */
    List<Practitioner> findAllByUserIdInAndIsDeletedFalse(Collection<Long> userIds);

    /**
     * 按业务号判断是否已占用（含软删，与工号全局唯一约束一致）。
     *
     * @param busNo 条件
     * @return 查询结果
     */
    @SkipSoftDeleteFilter
    boolean existsByBusNo(String busNo);

    /**
     * 查询标准采番格式（{@code PR} + 纯数字）工号的最大数字后缀（含软删）。
     * <p>用于创建前抬升 {@code zxplat_sequence}，避免序号落后于库内已有工号时撞号。</p>
     *
     * @return 最大后缀；无匹配行时为 {@code null}
     */
    @SkipSoftDeleteFilter
    @Query(value = "SELECT MAX(CAST(substring(bus_no FROM '[0-9]+$') AS BIGINT)) "
        + "FROM adm_practitioner WHERE bus_no ~ '^PR[0-9]+$'", nativeQuery = true)
    Long findMaxPrBusNoSuffix();

    /**
     * 按业务号查询。
     * @param busNo 条件
     * @param id 条件
     * @return 查询结果
     */
    boolean existsByBusNoAndIdNot(String busNo, Long id);

    /**
     * 按院内工号查询未删记录（小程序医护绑定等）。
     */
    Optional<Practitioner> findFirstByBusNoAndIsDeletedFalse(String busNo);

    /**
     * 按条件查询列表。
     * @param statusEnum 条件
     * @return 查询结果
     */
    List<Practitioner> findAllByStatusEnum(String statusEnum);

    /**
     * 按条件查询列表。
     * @param statusEnum 条件
     * @param orgId 条件
     * @return 查询结果
     */
    List<Practitioner> findAllByStatusEnumAndOrgId(String statusEnum, Long orgId);

    /**
     * 按条件查询列表。
     * @param pageable 条件
     * @return 查询结果
     */
    Page<Practitioner> findAllBy(Pageable pageable);
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.service;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.repository.PractitionerRepository;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 医务人员读侧查询服务。
 *
 * <p>职责：为挂号、人员维护等提供在职人员列表、分页检索与按主键加载。</p>
 *
 * <p>边界：只读；列表默认过滤 {@link PublicationStatus#ACTIVE}；按岗位收窄须由应用层 {@code PractitionerRoleScopeLoader} 预解析主键后传入。</p>
 * <p>聚合边界：仅本聚合 {@link PractitionerRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class PractitionerQueryService {

    private final PractitionerRepository practitionerRepository;

    /**
     * 门诊挂号可选医生列表：在职且主键落在应用层预解析的岗位人员集合内。
     *
     * @param resolvedPractitionerIdsForRole 由 {@code PractitionerRoleScopeLoader} 预解析；空集合时返回空列表
     * @return 医务人员列表
     */
    public List<Practitioner> listActivePractitionersByResolvedRoleScope(Collection<Long> resolvedPractitionerIdsForRole) {
        if (resolvedPractitionerIdsForRole == null || resolvedPractitionerIdsForRole.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<Practitioner> specification = PractitionerPageQuerySupport.activeOutpatientRegistrationDoctors(
            PublicationStatus.ACTIVE.getCode(),
            resolvedPractitionerIdsForRole);
        return practitionerRepository.findAll(specification);
    }

    /**
     * 分页浏览医务人员（条件下推至数据库分页）。
     *
     * @param pageNum  页码（0-based，与 Spring Data {@link PageRequest} 一致）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public Page<Practitioner> searchPractitionersPaged(int pageNum, int pageSize) {
        return practitionerRepository.findAllBy(PageRequest.of(pageNum, pageSize));
    }

    /**
     * 岗位相关场景：在职医务人员分页；{@code resolvedPractitionerIdsForRole} 为 {@code null} 时不按岗位过滤。
     */
    public Page<Practitioner> pageActivePractitionersForPosition(
        Collection<Long> resolvedPractitionerIdsForRole,
        String searchKey,
        Pageable pageable) {
        return pageActivePractitionersForPosition(resolvedPractitionerIdsForRole, searchKey, pageable, false);
    }

    /**
     * 岗位相关场景：在职医务人员分页（可要求已绑定系统用户）。
     *
     * @param resolvedPractitionerIdsForRole 应用层预解析的岗位人员主键；{@code null} 表示不按岗位过滤；空集合表示无命中
     */
    public Page<Practitioner> pageActivePractitionersForPosition(
        Collection<Long> resolvedPractitionerIdsForRole,
        String searchKey,
        Pageable pageable,
        boolean requireBoundSystemUser) {
        String status = PublicationStatus.ACTIVE.getCode();
        String trimmedKeyword = null;
        if (StringUtils.isNotEmpty(searchKey)) {
            trimmedKeyword = searchKey.trim();
        }
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 精确优先 ORDER BY（B.2.1）
        Pageable effective = pageable;
        if (StringUtils.isNotEmpty(trimmedKeyword) && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        Specification<Practitioner> specification =
            PractitionerPageQuerySupport.activePractitioners(
                status, trimmedKeyword, requireBoundSystemUser, resolvedPractitionerIdsForRole);
        return practitionerRepository.findAll(specification, effective);
    }

    /**
     * 按主键加载医务人员；不存在返回 {@code null}。
     *
     * @param practitionerId 医务人员主键
     * @return 实体或 {@code null}
     */
    public Practitioner findById(Long practitionerId) {
        return practitionerRepository.findById(practitionerId).orElse(null);
    }

    /**
     * 按院内工号加载在职医护。
     */
    public Optional<Practitioner> findActiveByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return Optional.empty();
        }
        return practitionerRepository.findFirstByBusNoAndIsDeletedFalse(busNo.trim())
            .filter(practitioner -> PublicationStatus.ACTIVE.getCode().equals(practitioner.getStatusEnum()));
    }

    /**
     * 按绑定的系统用户主键加载医务人员主档；未绑定或已删除则返回 {@code null}。
     *
     * @param userId 系统用户主键
     * @return 医务人员实体或 {@code null}
     */
    public Practitioner findByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return practitionerRepository.findFirstByUserIdAndIsDeletedFalseOrderByIdAsc(userId).orElse(null);
    }

    /**
     * 批量解析流水经办人展示名（{@code entererId} 为 {@code adm_practitioner.id}，**BR-OPC-016**）。
     *
     * <p>漏网 {@code zxplat_users.id} 由 Flyway {@code V23__backfill_enterer_id_practitioner} 回填；
     * 本方法<strong>不再</strong>按绑定用户主键二次反查。</p>
     *
     * @param entererIds 支付头 / 收费明细 {@code enterer_id} 集合
     * @return entererId → 姓名；未命中则不写入该键
     */
    public Map<Long, String> mapDisplayNameByEntererIds(java.util.Collection<Long> entererIds) {
        if (entererIds == null || entererIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.LinkedHashSet<Long> distinct = new java.util.LinkedHashSet<>();
        for (Long entererId : entererIds) {
            if (entererId != null) {
                distinct.add(entererId);
            }
        }
        if (distinct.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<Long, String> nameByEntererId = new java.util.HashMap<>(distinct.size() * 2);
        // Pre：一次按医务人员主键批量加载，禁止 findById 循环 N+1
        List<Practitioner> byPractitionerId = this.findAllByIds(distinct);
        for (Practitioner practitioner : byPractitionerId) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(practitioner.getName())) {
                nameByEntererId.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameByEntererId;
    }

    /**
     * 按主键集合批量加载医务人员（列表装配等场景避免 N+1）。
     *
     * <p>主键过多时按 {@link com.zxplat.domain.share.constants.GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批。</p>
     *
     * @param ids 医务人员主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<Practitioner> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return IdInQueryChunkSupport.loadByIdInChunks(
            ids,
            chunkIds -> {
                List<Practitioner> rows = practitionerRepository.findAllById(chunkIds);
                if (rows == null) {
                    return Collections.emptyList();
                }
                return rows;
            });
    }

    /**
     * 按姓名关键字模糊匹配在职医务人员主键（门诊病历接诊医生筛选等）。
     *
     * <p>聚合边界：仅本聚合 {@link PractitionerRepository}。</p>
     *
     * @param nameKeyword 姓名关键字；空白时返回空列表
     * @return 命中主键列表（去重保序）
     */
    public List<Long> listActivePractitionerIdsByNameKeyword(String nameKeyword) {
        if (StringUtils.isEmpty(nameKeyword)) {
            return Collections.emptyList();
        }
        String trimmed = nameKeyword.trim();
        String status = PublicationStatus.ACTIVE.getCode();
        Specification<Practitioner> specification =
            PractitionerPageQuerySupport.activePractitioners(status, trimmed, false, null);
        List<Practitioner> rows = practitionerRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Practitioner row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            ids.add(row.getId());
        }
        return ids;
    }

    /**
     * 批量解析医务人员展示名。
     *
     * @param practitionerIds 医务人员主键集合
     * @return practitionerId → 姓名；未命中或姓名空则不写入该键
     */
    public Map<Long, String> mapNamesByIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Practitioner> rows = findAllByIds(practitionerIds);
        java.util.Map<Long, String> result = new java.util.HashMap<>();
        for (Practitioner row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(row.getName())) {
                continue;
            }
            result.put(row.getId(), row.getName());
        }
        return result;
    }

    /**
     * 按绑定系统用户主键批量加载在职医务人员（财务审批人下拉等）。
     *
     * @param userIds 系统用户主键集合
     * @return 在职且未软删的医务人员；入参为空时返回空列表
     */
    public List<Practitioner> listActivePractitionersByBoundUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Practitioner> found = practitionerRepository.findAllByUserIdInAndIsDeletedFalse(userIds);
        if (found == null || found.isEmpty()) {
            return Collections.emptyList();
        }
        String active = PublicationStatus.ACTIVE.getCode();
        List<Practitioner> result = new ArrayList<>();
        for (Practitioner practitioner : found) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (!active.equals(practitioner.getStatusEnum())) {
                continue;
            }
            result.add(practitioner);
        }
        return result;
    }

    /**
     * 按医务人员主键批量取出已绑定的平台用户主键。
     *
     * <p>仅本聚合 {@link PractitionerRepository}；未绑定 {@code user_id} 的主档不进入结果。
     * 供用户管理按出诊科室检索：岗位 QS 先给出 practitionerId，再在本聚合映射账号。</p>
     *
     * @param practitionerIds 医务人员主键集合
     * @return 去重保序的 {@code zxplat_users.id}；入参空时返回空列表
     */
    public List<Long> listBoundUserIdsByPractitionerIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            // 无岗位命中：用户管理按科室检索应得到空列表
            return Collections.emptyList();
        }
        List<Practitioner> rows = this.findAllByIds(practitionerIds);
        java.util.LinkedHashSet<Long> userIds = new java.util.LinkedHashSet<>();
        for (Practitioner row : rows) {
            if (row == null || row.getUserId() == null) {
                // 未绑定系统用户：用户管理列表无法展示，跳过
                continue;
            }
            userIds.add(row.getUserId());
        }
        return new ArrayList<>(userIds);
    }
}

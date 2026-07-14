/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitioner.service;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.administration.practitioner.repository.PractitionerRepository;
import com.openhis.health.domain.share.enums.PublicationStatus;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        String searchPattern = null;
        if (StringUtils.isNotEmpty(searchKey)) {
            searchPattern = SearchCodeUtil.toCaseInsensitiveLikePattern(searchKey.trim());
        }
        Specification<Practitioner> specification =
            PractitionerPageQuerySupport.activePractitioners(
                status, searchPattern, requireBoundSystemUser, resolvedPractitionerIdsForRole);
        return practitionerRepository.findAll(specification, pageable);
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
     * 批量解析流水经办人展示名（{@code entererId} 为医务人员主键；存量数据可能仍为系统用户 ID）。
     *
     * @param entererIds 支付头 {@code enterer_id} 集合
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
        java.util.List<Long> unresolved = new java.util.ArrayList<>();
        for (Long entererId : distinct) {
            Practitioner practitioner = practitionerRepository.findById(entererId).orElse(null);
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameByEntererId.put(practitioner.getId(), practitioner.getName());
            } else {
                unresolved.add(entererId);
            }
        }
        if (!unresolved.isEmpty()) {
            java.util.List<Practitioner> byUserId =
                practitionerRepository.findAllByUserIdInAndIsDeletedFalse(unresolved);
            for (Practitioner practitioner : byUserId) {
                if (practitioner == null || practitioner.getUserId() == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(practitioner.getName())) {
                    nameByEntererId.put(practitioner.getUserId(), practitioner.getName());
                }
            }
        }
        return nameByEntererId;
    }

    /**
     * 按主键集合批量加载医务人员（列表装配等场景避免 N+1）。
     *
     * @param ids 医务人员主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<Practitioner> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return practitionerRepository.findAllById(ids);
    }
}

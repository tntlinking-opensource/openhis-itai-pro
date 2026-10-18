/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.service;

import com.zxplat.plugin.pacs.domain.workitem.entity.PacsModalityCapability;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsModalityCapabilityQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsModalityCapabilityRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模态能力读侧（BR-PWI-03 / AS-PACS-CFG-001）。
 *
 * <p>仅本聚合 {@link PacsModalityCapabilityRepository}；含接单覆盖校验与配置台分页列表。</p>
 */
@Service
@RequiredArgsConstructor
public class ModalityCapabilityQueryService {

    private final PacsModalityCapabilityRepository modalityCapabilityRepository;

    /**
     * 分页查询能力配置（租户隔离、未软删）。
     *
     * @param modalityCode 模态过滤（可空）
     * @param enabledOnly 仅启用时为 true
     * @param pageable 分页；空则默认第 0 页 20 条按 updatedAt DESC
     * @return 读模型页
     */
    public Page<PacsModalityCapabilityQueryModel> search(
        String modalityCode, Boolean enabledOnly, Pageable pageable) {
        Specification<PacsModalityCapability> specification =
            SpecificationUtils.<PacsModalityCapability>tenantFilter()
                .and(SpecificationUtils.notDeleted());
        // 模态精确过滤：配置台按设备类型收窄
        if (StringUtils.isNotEmpty(modalityCode)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("modalityCode"), modalityCode.trim()));
        }
        // 仅启用：接单校验与下拉不展示停用能力
        if (Boolean.TRUE.equals(enabledOnly)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("enabledFlag"), Boolean.TRUE));
        }
        Pageable resolved = this.resolvePageable(pageable);
        return modalityCapabilityRepository.findAll(specification, resolved).map(this::toModel);
    }

    /**
     * 校验全部 activity 均具备启用态模态能力配置。
     *
     * @param activityIds ActivityDefinition 主键列表
     * @param tenantId 租户主键
     * @throws BusinessException 存在未覆盖 activity 时
     */
    public void assertAllActivitiesCovered(List<Long> activityIds, Long tenantId) {
        List<Long> distinctIds = this.distinctNonNullIds(activityIds);
        if (distinctIds.isEmpty()) {
            return;
        }
        List<PacsModalityCapability> capabilities = modalityCapabilityRepository
            .findByActivityIdInAndTenantIdAndEnabledFlagTrue(distinctIds, tenantId);
        Set<Long> covered = new HashSet<>();
        for (PacsModalityCapability capability : capabilities) {
            covered.add(capability.getActivityId());
        }
        for (Long activityId : distinctIds) {
            // 任一 activity 无启用能力则整批拒绝接单
            if (!covered.contains(activityId)) {
                throw new BusinessException(
                    ErrorCodeConstants.PACS_MODALITY_CAPABILITY_MISSING,
                    "检查项目未配置模态能力: activityId=" + activityId);
            }
        }
    }

    /**
     * 解析 activity 对应的启用态模态码（取首条能力配置）。
     *
     * @param activityId ActivityDefinition 主键
     * @param tenantId 租户主键
     * @return 模态码
     */
    public String resolveModalityCode(Long activityId, Long tenantId) {
        this.assertAllActivitiesCovered(List.of(activityId), tenantId);
        List<PacsModalityCapability> capabilities = modalityCapabilityRepository
            .findByActivityIdInAndTenantIdAndEnabledFlagTrue(List.of(activityId), tenantId);
        return capabilities.get(0).getModalityCode();
    }

    /**
     * 去重并剔除空 activityId。
     *
     * @param activityIds 原始 activity 列表
     * @return 去重后的非空 id 列表
     */
    private List<Long> distinctNonNullIds(List<Long> activityIds) {
        List<Long> distinctIds = new ArrayList<>();
        if (activityIds == null) {
            return distinctIds;
        }
        Set<Long> seen = new HashSet<>();
        for (Long activityId : activityIds) {
            if (activityId == null) {
                continue;
            }
            if (seen.add(activityId)) {
                distinctIds.add(activityId);
            }
        }
        return distinctIds;
    }

    /**
     * 解析分页：空 pageable 用默认页；无显式 Sort 时按 updatedAt DESC。
     *
     * @param pageable 入参分页
     * @return 可交给 Repository 的 Pageable
     */
    private Pageable resolvePageable(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        Sort sort = pageable.getSort();
        if (!sort.isSorted()) {
            sort = defaultSort;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * 实体转读模型（配置台列表字段）。
     *
     * @param entity 能力实体
     * @return 读模型
     */
    private PacsModalityCapabilityQueryModel toModel(PacsModalityCapability entity) {
        PacsModalityCapabilityQueryModel model = new PacsModalityCapabilityQueryModel();
        model.setId(entity.getId());
        model.setActivityId(entity.getActivityId());
        model.setModalityCode(entity.getModalityCode());
        model.setEnabledFlag(entity.getEnabledFlag());
        model.setOrganizationId(entity.getOrganizationId());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }
}

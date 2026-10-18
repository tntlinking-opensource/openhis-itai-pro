/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.device.service;

import com.zxplat.plugin.pacs.domain.device.entity.PacsDevice;
import com.zxplat.plugin.pacs.domain.device.queries.model.PacsDeviceQueryModel;
import com.zxplat.plugin.pacs.domain.device.repository.PacsDeviceRepository;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 检查设备台账读侧（DM-PACS-005 / AS-PACS-CFG-003）。
 *
 * <p>仅本聚合 {@link PacsDeviceRepository}；分页列表供配置台，不含写侧不变量。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsDeviceQueryService {

    private final PacsDeviceRepository deviceRepository;

    /**
     * 分页查询设备（租户隔离、未软删）。
     *
     * @param modalityCode 模态过滤（可空）
     * @param enabledOnly 仅启用时为 true
     * @param keyword 名称或编码模糊（可空）
     * @param pageable 分页；空则默认第 0 页 20 条并按 sortOrder/updatedAt
     * @return 读模型页
     */
    public Page<PacsDeviceQueryModel> search(
        String modalityCode, Boolean enabledOnly, String keyword, Pageable pageable) {
        Specification<PacsDevice> specification = SpecificationUtils.<PacsDevice>tenantFilter()
            .and(SpecificationUtils.notDeleted());
        // 模态精确过滤：配置台按设备类型收窄
        if (StringUtils.isNotEmpty(modalityCode)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("modalityCode"), modalityCode.trim()));
        }
        // 仅启用：接单/排程下拉不展示停用设备
        if (Boolean.TRUE.equals(enabledOnly)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("enabledFlag"), Boolean.TRUE));
        }
        // 关键字：名称或编码模糊，便于台账检索
        if (StringUtils.isNotEmpty(keyword)) {
            String pattern = "%" + keyword.trim() + "%";
            specification = specification.and((root, cq, cb) -> cb.or(
                cb.like(root.get("name"), pattern),
                cb.like(root.get("deviceCode"), pattern)));
        }
        Sort defaultSort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("updatedAt"));
        Pageable resolved = this.resolvePageable(pageable, defaultSort);
        return deviceRepository.findAll(specification, resolved).map(this::toModel);
    }

    /**
     * 解析分页：空 pageable 用默认页；无显式 Sort 时套用默认排序。
     *
     * @param pageable 入参分页
     * @param defaultSort 默认排序
     * @return 可交给 Repository 的 Pageable
     */
    private Pageable resolvePageable(Pageable pageable, Sort defaultSort) {
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        Sort sort = defaultSort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * 实体转读模型（列表字段）。
     *
     * @param entity 设备实体
     * @return 读模型
     */
    private PacsDeviceQueryModel toModel(PacsDevice entity) {
        PacsDeviceQueryModel model = new PacsDeviceQueryModel();
        model.setId(entity.getId());
        model.setDeviceCode(entity.getDeviceCode());
        model.setName(entity.getName());
        model.setModalityCode(entity.getModalityCode());
        model.setAeTitle(entity.getAeTitle());
        model.setStationName(entity.getStationName());
        model.setEnabledFlag(entity.getEnabledFlag());
        model.setSortOrder(entity.getSortOrder());
        return model;
    }
}

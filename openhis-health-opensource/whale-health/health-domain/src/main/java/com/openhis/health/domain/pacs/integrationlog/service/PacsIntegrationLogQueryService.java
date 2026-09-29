/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.integrationlog.service;

import com.openhis.health.domain.pacs.integrationlog.entity.PacsIntegrationLog;
import com.openhis.health.domain.pacs.integrationlog.queries.model.PacsIntegrationLogQueryModel;
import com.openhis.health.domain.pacs.integrationlog.repository.PacsIntegrationLogRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * PACS 集成日志读侧查询（AS-PACS-LOG-001）。
 */
@Service
@RequiredArgsConstructor
public class PacsIntegrationLogQueryService {

    private final PacsIntegrationLogRepository repository;

    /**
     * 条件分页查询（未软删；默认 createdAt DESC）。
     *
     * @param directionEnum 方向（可空）
     * @param actionCode 动作码（可空）
     * @param outcomeEnum 结果（可空）
     * @param entityType 业务类型（可空）
     * @param entityId 业务主键（可空）
     * @param createdFrom 创建起（含，可空）
     * @param createdTo 创建止（含，可空）
     * @param pageable 分页
     * @return 读模型页
     */
    public Page<PacsIntegrationLogQueryModel> search(
        String directionEnum,
        String actionCode,
        String outcomeEnum,
        String entityType,
        Long entityId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Pageable pageable) {
        Specification<PacsIntegrationLog> specification = SpecificationUtils.<PacsIntegrationLog>tenantFilter()
            .and(SpecificationUtils.notDeleted());
        if (StringUtils.isNotEmpty(directionEnum)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("directionEnum"), directionEnum.trim()));
        }
        if (StringUtils.isNotEmpty(actionCode)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("actionCode"), actionCode.trim()));
        }
        if (StringUtils.isNotEmpty(outcomeEnum)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("outcomeEnum"), outcomeEnum.trim()));
        }
        if (StringUtils.isNotEmpty(entityType)) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("entityType"), entityType.trim()));
        }
        if (entityId != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (createdFrom != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }
        if (createdTo != null) {
            specification = specification.and(
                (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }
        Pageable resolved = this.resolveCreatedAtDescPageable(pageable);
        return repository.findAll(specification, resolved).map(this::toModel);
    }

    /**
     * 按主键取详情（未软删）。
     *
     * @param id 主键
     * @return 读模型
     */
    public PacsIntegrationLogQueryModel getById(Long id) {
        if (id == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_INTEGRATION_LOG_INVALID_ARGUMENT, "日志主键不能为空");
        }
        PacsIntegrationLog entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_INTEGRATION_LOG_NOT_FOUND, "集成日志不存在"));
        return this.toModel(entity);
    }

    private Pageable resolveCreatedAtDescPageable(Pageable pageable) {
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable == null) {
            return PageRequest.of(0, 20, defaultSort);
        }
        if (pageable.getSort().isSorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private PacsIntegrationLogQueryModel toModel(PacsIntegrationLog entity) {
        PacsIntegrationLogQueryModel model = new PacsIntegrationLogQueryModel();
        model.setId(entity.getId());
        model.setDirectionEnum(entity.getDirectionEnum());
        model.setActionCode(entity.getActionCode());
        model.setEntityType(entity.getEntityType());
        model.setEntityId(entity.getEntityId());
        model.setOutcomeEnum(entity.getOutcomeEnum());
        model.setRequestBody(entity.getRequestBody());
        model.setResponseBody(entity.getResponseBody());
        model.setErrorMessage(entity.getErrorMessage());
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.contrast.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.application.support.BossTenantSupport;
import com.zxplat.plugin.einvoice.boss.application.contrast.response.ChargeContrastView;
import com.zxplat.plugin.einvoice.boss.application.contrast.response.PageContrastsResponse;
import com.zxplat.plugin.einvoice.boss.application.contrast.request.ContrastIdRequest;
import com.zxplat.plugin.einvoice.boss.application.contrast.request.PageContrastsRequest;
import com.zxplat.plugin.einvoice.boss.application.contrast.request.SaveChargeContrastRequest;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.contrast.entity.EinvChargeContrast;
import com.zxplat.plugin.einvoice.boss.domain.contrast.repository.EinvChargeContrastRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 博思收费对照维护页（HTTP：{@code /api/app/einvoiceBossContrast/*}，P2）。
 *
 * <p>维护 health internalCode ↔ 博思 chargeCode 映射，供 {@link com.zxplat.plugin.einvoice.boss.application.internal.document.EinvoiceBossDocumentInitializerService} 出站明细解析。</p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossContrastAppService {

    private final EinvChargeContrastRepository contrastRepository;

    /**
     * 分页查询收费对照。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageContrastsResponse pageContrasts(PageContrastsRequest request) {
        PageContrastsRequest safe = request == null ? new PageContrastsRequest() : request;
        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : safe.getPageSize();
        Long tenantId = requireTenantId();

        Specification<EinvChargeContrast> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted"))));
            if (StringUtils.hasText(safe.getKeyword())) {
                String keyword = "%" + safe.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("internalCode")), keyword),
                        cb.like(cb.lower(root.get("bossChargeCode")), keyword),
                        cb.like(cb.lower(root.get("bossChargeName")), keyword)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<EinvChargeContrast> page = contrastRepository.findAll(
                spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt")));

        PageContrastsResponse response = new PageContrastsResponse();
        response.setTotal(page.getTotalElements());
        List<ChargeContrastView> rows = new ArrayList<>();
        for (EinvChargeContrast entity : page.getContent()) {
            rows.add(toDto(entity));
        }
        response.setRows(rows);
        return response;
    }

    /**
     * 新增或更新收费对照。
     *
     * @param request 保存请求
     * @return 保存后的对照
     */
    public ChargeContrastView saveContrast(SaveChargeContrastRequest request) {
        if (request == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "请求不能为空");
        }
        String internalCode = normalizeCode(request.getInternalCode());
        String bossChargeCode = normalizeCode(request.getBossChargeCode());
        String bossChargeName = request.getBossChargeName() == null ? "" : request.getBossChargeName().trim();
        if (!StringUtils.hasText(internalCode)) {
            throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "院内编码不能为空");
        }
        if (!StringUtils.hasText(bossChargeCode)) {
            throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "博思收费编码不能为空");
        }

        Long tenantId = requireTenantId();
        EinvChargeContrast entity;
        if (request.getId() != null) {
            entity = contrastRepository.findById(request.getId())
                    .filter(row -> tenantId.equals(row.getTenantId()))
                    .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                    .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "收费对照不存在"));
            if (contrastRepository.findByTenantIdAndInternalCodeAndIsDeletedFalseAndIdNot(
                    tenantId, internalCode, entity.getId()).isPresent()) {
                throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_DUPLICATE, "院内编码已存在");
            }
        } else {
            if (contrastRepository.findByTenantIdAndInternalCodeAndIsDeletedFalse(tenantId, internalCode).isPresent()) {
                throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_DUPLICATE, "院内编码已存在");
            }
            entity = new EinvChargeContrast();
            entity.setTenantId(tenantId);
            entity.setIsDeleted(false);
            entity.setCreatedAt(LocalDateTime.now());
        }

        entity.setOrgId(request.getOrgId());
        entity.setInternalCode(internalCode);
        entity.setBossChargeCode(bossChargeCode);
        entity.setBossChargeName(bossChargeName);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(contrastRepository.save(entity));
    }

    /**
     * 软删除收费对照。
     *
     * @param request 主键请求
     */
    public void deleteContrast(ContrastIdRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "对照主键不能为空");
        }
        Long tenantId = requireTenantId();
        EinvChargeContrast entity = contrastRepository.findById(request.getId())
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_CONTRAST_NOT_FOUND, "收费对照不存在"));
        entity.setIsDeleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        contrastRepository.save(entity);
    }

    /** 实体 → 列表/详情 DTO。 */
    private ChargeContrastView toDto(EinvChargeContrast entity) {
        ChargeContrastView dto = new ChargeContrastView();
        dto.setId(entity.getId());
        dto.setOrgId(entity.getOrgId());
        dto.setInternalCode(entity.getInternalCode());
        dto.setBossChargeCode(entity.getBossChargeCode());
        dto.setBossChargeName(entity.getBossChargeName());
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt());
        return dto;
    }

    /** 编码 trim + 大写（院内码、博思码统一格式）。 */
    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Long requireTenantId() {
        return BossTenantSupport.requireCurrentTenantId();
    }
}

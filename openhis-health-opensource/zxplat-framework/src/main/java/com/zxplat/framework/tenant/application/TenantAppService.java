/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.tenant.application;

import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.PageRequestUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.PlatformTenantAccessPolicy;
import com.zxplat.framework.core.infrastructure.permission.RequirePermission;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.tenant.application.dto.*;
import com.zxplat.framework.tenant.domain.Tenant;
import com.zxplat.framework.tenant.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 租户应用服务
 *
 * <p>提供租户的CRUD功能，自动暴露为REST API</p>
 *
 * @author OpenHIS Contributors
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAppService {

    private final TenantRepository tenantRepository;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final Optional<PlatformTenantAccessPolicy> platformTenantAccessPolicy;

    /**
     * 根据ID获取租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public TenantDto getById(Long id) {
        assertCanViewTenant(id);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("租户", id));
        return toDto(tenant);
    }

    /**
     * 根据代码获取租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public TenantDto getByCode(String code) {
        Tenant tenant = tenantRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException(String.format("租户 (代码: %s) 不存在", code)));
        assertCanViewTenant(tenant.getId());
        return toDto(tenant);
    }

    /**
     * 获取租户列表（分页）
     */
    @RequirePermission("Navigation.TenantManagement")
    public PagedResult<TenantDto> getList(TenantQueryRequest request) {
        Specification<Tenant> spec = buildSpecification(request);
        PageRequest pageRequest = PageRequestUtils.createDefault(request.getPageNo(), request.getPageSize());

        Page<Tenant> page = tenantRepository.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDto));
    }

    /**
     * 创建租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public TenantDto create(CreateTenantRequest request) {
        assertCanMaintainTenantCatalog();
        // 检查代码是否已存在
        if (tenantRepository.existsByCode(request.getCode())) {
            throw new ValidationException("租户代码已存在: " + request.getCode());
        }

        // 检查名称是否已存在
        if (tenantRepository.existsByName(request.getName())) {
            throw new ValidationException("租户名称已存在: " + request.getName());
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setCode(request.getCode());
        tenant.setDescription(request.getDescription());
        tenant.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        tenant.setExtraProperties(request.getExtraProperties());

        tenant = tenantRepository.save(tenant);
        log.info("Created tenant: {} ({})", tenant.getId(), tenant.getCode());
        return toDto(tenant);
    }

    /**
     * 更新租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public TenantDto update(Long id, UpdateTenantRequest request) {
        assertCanMaintainTenantCatalog();
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("租户", id));

        if (request.getName() != null) {
            // 检查名称是否被其他租户使用
            tenantRepository.findAll().stream()
                    .filter(t -> t.getName().equals(request.getName()) && !t.getId().equals(id))
                    .findFirst()
                    .ifPresent(t -> {
                        throw new ValidationException("租户名称已被其他租户使用");
                    });
            tenant.setName(request.getName());
        }

        if (request.getDescription() != null) {
            tenant.setDescription(request.getDescription());
        }

        if (request.getIsActive() != null) {
            tenant.setIsActive(request.getIsActive());
        }

        if (request.getExtraProperties() != null) {
            tenant.setExtraProperties(request.getExtraProperties());
        }

        tenant = tenantRepository.save(tenant);
        log.info("Updated tenant: {} ({})", tenant.getId(), tenant.getCode());
        return toDto(tenant);
    }

    /**
     * 删除租户（软删除）
     */
    @RequirePermission("Navigation.TenantManagement")
    public void delete(Long id) {
        assertCanMaintainTenantCatalog();
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("租户", id));

        // 软删除：设置删除标记，而不是调用repository.delete()
        tenant.setIsDeleted(true);
        tenant.setDeletedAt(LocalDateTime.now());

        // 设置删除人
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        if (currentUserId != null) {
            tenant.setDeletedBy(currentUserId);
        }

        tenantRepository.save(tenant);
        log.info("Deleted tenant: {} ({})", tenant.getId(), tenant.getCode());
    }

    /**
     * 启用租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public void enable(Long id) {
        assertCanMaintainTenantCatalog();
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("租户", id));
        tenant.setIsActive(true);
        tenantRepository.save(tenant);
        log.info("Enabled tenant: {} ({})", tenant.getId(), tenant.getCode());
    }

    /**
     * 禁用租户
     */
    @RequirePermission("Navigation.TenantManagement")
    public void disable(Long id) {
        assertCanMaintainTenantCatalog();
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("租户", id));
        tenant.setIsActive(false);
        tenantRepository.save(tenant);
        log.info("Disabled tenant: {} ({})", tenant.getId(), tenant.getCode());
    }

    /**
     * 构建查询条件
     */
    private Specification<Tenant> buildSpecification(TenantQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 关键词查询（名称或代码）
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                String keyword = "%" + request.getKeyword().trim() + "%";
                Predicate nameLike = cb.like(root.get("name"), keyword);
                Predicate codeLike = cb.like(root.get("code"), keyword);
                predicates.add(cb.or(nameLike, codeLike));
            }

            // 代码精确查询
            if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
                predicates.add(cb.equal(root.get("code"), request.getCode().trim()));
            }

            // 激活状态查询
            if (request.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), request.getIsActive()));
            }

            // 软删除过滤
            predicates.add(cb.equal(root.get("isDeleted"), false));

            List<Long> visibleTenantIds = resolveVisibleTenantIds();
            if (visibleTenantIds != null) {
                if (visibleTenantIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(visibleTenantIds));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void assertCanViewTenant(Long tenantId) {
        platformTenantAccessPolicy.ifPresent(policy -> {
            Long userId = currentOperatorResolver.findOperatorIdOrNull();
            if (policy.isPlatformAdmin(userId)) {
                return;
            }
            policy.assertCanManageTenant(userId, tenantId);
        });
    }

    private void assertCanMaintainTenantCatalog() {
        platformTenantAccessPolicy.ifPresent(policy ->
            policy.assertCanMaintainTenantCatalog(currentOperatorResolver.findOperatorIdOrNull()));
    }

    private List<Long> resolveVisibleTenantIds() {
        if (platformTenantAccessPolicy.isEmpty()) {
            return null;
        }
        return platformTenantAccessPolicy.get().resolveVisibleTenantIds(currentOperatorResolver.findOperatorIdOrNull());
    }

    /**
     * 转换为DTO
     */
    private TenantDto toDto(Tenant tenant) {
        TenantDto dto = new TenantDto();
        BeanUtils.copyProperties(tenant, dto);
        return dto;
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.setting.domain.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.identity.domain.UserRole;
import com.zxplat.framework.identity.infrastructure.repository.UserRoleRepository;
import com.zxplat.framework.setting.domain.Setting;
import com.zxplat.framework.setting.domain.SettingProviderName;
import com.zxplat.framework.setting.infrastructure.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 设置领域管理器（封装 Repository 访问 + 设置取值规则）
 */
@Service
@RequiredArgsConstructor
public class SettingManager {

    private final SettingRepository settingRepository;
    private final ICurrentUser currentUser;
    private final UserRoleRepository userRoleRepository;

    /**
     * 根据ID查询设置。
     *
     * @param id 设置主键
     * @return 设置实体（不存在返回 empty）
     */
    public Optional<Setting> findById(Long id) {
        return settingRepository.findById(id);
    }

    /**
     * 根据名称查询设置（不区分提供者）。
     *
     * @param name 设置名称
     * @return 设置实体（不存在返回 empty）
     */
    public Optional<Setting> findByName(String name) {
        return settingRepository.findByName(name);
    }

    /**
     * 按 (name + providerName + providerKey) 查询设置。
     *
     * @param name 设置名称
     * @param providerName 提供者名称（G/U/R/T）
     * @param providerKey 提供者键（G 可为空；U/R/T 必填）
     * @return 设置实体（不存在返回 empty）
     */
    public Optional<Setting> findByNameAndProvider(String name, String providerName, String providerKey) {
        return settingRepository.findByNameAndProviderNameAndProviderKey(name, providerName, providerKey);
    }

    /**
     * 按 Specification 查询单条记录。
     */
    public Optional<Setting> findOne(Specification<Setting> spec) {
        return settingRepository.findOne(spec);
    }

    /**
     * 按 Specification 分页查询。
     */
    public Page<Setting> findAll(Specification<Setting> spec, Pageable pageable) {
        return settingRepository.findAll(spec, pageable);
    }

    /**
     * 判断 name 是否存在（不区分提供者）。
     */
    public boolean existsByName(String name) {
        return settingRepository.existsByName(name);
    }

    /**
     * 判断 (name + providerName + providerKey) 是否存在。
     */
    public boolean existsByNameAndProvider(String name, String providerName, String providerKey) {
        return settingRepository.existsByNameAndProviderNameAndProviderKey(name, providerName, providerKey);
    }

    /**
     * 保存设置（新增或更新）。
     */
    public Setting save(Setting setting) {
        return settingRepository.save(setting);
    }

    /**
     * 删除设置（由上层决定是软删还是物理删；当前 Repository 默认 delete）。
     */
    public void delete(Setting setting) {
        if (setting == null) {
            return;
        }
        setting.setIsDeleted(true);
        setting.setDeletedAt(LocalDateTime.now());
        Long userId = currentUser != null ? currentUser.getCurrentUserId() : null;
        if (userId != null) {
            setting.setDeletedBy(userId);
        }
        settingRepository.save(setting);
    }

    /**
     * 删除指定提供者下的全部设置。
     */
    public void deleteByProvider(String providerName, String providerKey) {
        List<Setting> list = settingRepository.findByProviderNameAndProviderKey(providerName, providerKey);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Setting s : list) {
            delete(s);
        }
    }

    /**
     * 按提供者优先级获取最终值（U > R > T > G）。
     *
     * <p>多租户下的 G：先取 tenant_id=当前租户 的 G，取不到再降级取 tenant_id=null 的 G。</p>
     *
     * <p>当用户存在多个角色，且角色配置 value 冲突时抛出异常。</p>
     *
     * @param name 设置名称
     * @return 设置值（不存在返回 null）
     */
    public String getEffectiveValue(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("设置名称不能为空");
        }
        String normalizedName = name.trim();

        Long tenantId = TenantUtils.getCurrentTenantId();
        Long userId = currentUser != null ? currentUser.getCurrentUserId() : null;

        // 1) U - 用户（providerKey = userId）
        if (userId != null && tenantId != null) {
            Optional<String> userValue =
                findValue(normalizedName, SettingProviderName.U.getCode(), String.valueOf(userId), tenantId);
            if (userValue.isPresent()) {
                return userValue.get();
            }
        }

        // 2) R - 角色（providerKey = roleId）；多角色冲突需抛异常
        if (userId != null && tenantId != null) {
            List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
            if (userRoles != null && !userRoles.isEmpty()) {
                Set<String> matchedValues = new HashSet<>();
                String matchedValue = null;

                for (UserRole userRole : userRoles) {
                    if (userRole == null || userRole.getRoleId() == null) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(userRole.getIsDeleted())) {
                        continue;
                    }

                    Optional<String> roleValue = findValue(normalizedName, SettingProviderName.R.getCode(),
                        String.valueOf(userRole.getRoleId()), tenantId);
                    if (roleValue.isEmpty()) {
                        continue;
                    }

                    matchedValue = roleValue.get();
                    matchedValues.add(matchedValue);

                    if (matchedValues.size() > 1) {
                        throw new BusinessException(
                            "ROLE_SETTING_VALUE_CONFLICT",
                            String.format("设置 [%s] 在多个角色上存在冲突的值，请检查角色配置", normalizedName)
                        );
                    }
                }

                if (matchedValue != null) {
                    return matchedValue;
                }
            }
        }

        // 3) T - 租户（providerKey = tenantId）
        if (tenantId != null) {
            Optional<String> tenantValue =
                findValue(normalizedName, SettingProviderName.T.getCode(), String.valueOf(tenantId), tenantId);
            if (tenantValue.isPresent()) {
                return tenantValue.get();
            }
        }

        // 4) G - 全局（providerKey 为空）
        Optional<String> global = tenantId != null
            ? findValue(normalizedName, SettingProviderName.G.getCode(), null, tenantId)
                .or(() -> findValue(normalizedName, SettingProviderName.G.getCode(), null, null))
            : findValue(normalizedName, SettingProviderName.G.getCode(), null, null);
        return global.orElse(null);
    }

    private Optional<String> findValue(String name, String providerName, String providerKey, Long tenantId) {
        Specification<Setting> spec = (root, query, cb) -> cb.and(
            cb.equal(root.get("isDeleted"), false),
            cb.equal(root.get("name"), name),
            cb.equal(root.get("providerName"), providerName)
        );

        // tenantId 匹配规则：
        // - U/R/T：必须 tenantId=当前租户
        // - G：由调用者决定（可传当前租户或 null），用于支持“租户全局”与“系统全局”两种形态
        if (!SettingProviderName.G.getCode().equals(providerName)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
        } else {
            if (tenantId == null) {
                spec = spec.and((root, query, cb) -> cb.isNull(root.get("tenantId")));
            } else {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
            }
        }

        if (providerKey == null || providerKey.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.isNull(root.get("providerKey")),
                cb.equal(root.get("providerKey"), "")
            ));
        } else {
            String normalizedKey = providerKey.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("providerKey"), normalizedKey));
        }

        return settingRepository.findOne(spec).map(Setting::getValue);
    }
}

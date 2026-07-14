/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application;

import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.PageRequestUtils;
import com.openhis.whale.core.application.util.SpecificationUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.domain.User;
import com.openhis.whale.identity.domain.UserTenant;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import com.openhis.whale.identity.infrastructure.repository.UserRepository;
import com.openhis.whale.identity.infrastructure.repository.UserTenantRepository;
import com.openhis.whale.setting.application.dto.*;
import com.openhis.whale.setting.domain.Setting;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.SettingProviderName;
import com.openhis.whale.setting.domain.service.SettingManager;
import com.openhis.whale.tenant.domain.Tenant;
import com.openhis.whale.tenant.infrastructure.repository.TenantRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设置应用服务
 *
 * <p>
 * 提供设置的CRUD功能，支持多租户和提供者模式
 * </p>
 *
 * @author Whale Framework
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingAppService {

    private final SettingManager settingManager;
    private final UserTenantRepository userTenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;

    /**
     * 根据ID获取设置详情。
     *
     * @param id 设置主键
     * @return 设置DTO
     * @throws EntityNotFoundException 设置不存在
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto getById(Long id) {
        Setting setting = settingManager.findById(id).orElseThrow(() -> new EntityNotFoundException("设置", id));
        return toDto(setting);
    }

    /**
     * 根据名称获取设置（不区分提供者）。
     *
     * @param name 设置名称
     * @return 设置DTO
     * @throws EntityNotFoundException 设置不存在
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto getByName(String name) {
        Setting setting = settingManager.findByName(name)
            .orElseThrow(() -> new EntityNotFoundException(String.format("设置 (名称: %s) 不存在", name)));
        return toDto(setting);
    }

    /**
     * 根据名称与提供者定位设置。
     *
     * @param name 设置名称
     * @param providerName 提供者名称（G/U/R/T）
     * @param providerKey 提供者键（G 可为空；U/R/T 必填）
     * @return 设置DTO
     * @throws EntityNotFoundException 设置不存在
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto getByNameAndProvider(String name, String providerName, String providerKey) {
        Setting setting = settingManager.findByNameAndProvider(name, providerName, providerKey)
            .orElseThrow(() -> new EntityNotFoundException(String.format("设置 (名称: %s) 不存在", name)));
        return toDto(setting);
    }

    /**
     * 获取设置值（不区分提供者）。
     *
     * @param name 设置名称
     * @return 设置值，不存在返回 null
     */
    @RequirePermission("Navigation.SettingManagement")
    public String getValue(String name) {
        return settingManager.findByName(name).map(Setting::getValue).orElse(null);
    }

    /**
     * 获取设置值（不区分提供者），不存在返回默认值。
     *
     * @param request 请求（name + defaultValue）
     * @return 设置值或默认值
     */
    @RequirePermission("Navigation.SettingManagement")
    public String getValueOrDefault(GetValueOrDefaultRequest request) {
        return settingManager.findByName(request.getName()).map(Setting::getValue).orElse(request.getDefaultValue());
    }

    /**
     * 初始化设置模块所需的静态数据（提供者名称下拉）。
     *
     * @return 初始化DTO（providers 为 value/label 列表）
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingInitDto init() {
        List<OptionDto> providers = SettingProviderName.priorityOrder().stream()
            .map(p -> new OptionDto(0l, p.getCode(), p.getLabel())).collect(Collectors.toList());

        SettingInitDto init = new SettingInitDto();
        init.setProviders(providers);
        return init;
    }

    /**
     * 初始化配置项定义列表（配置源枚举）。
     *
     * <p>
     * 用于配置维护页面的“主列表”。
     * </p>
     *
     * @return 配置项定义列表（title + name）
     */
    @RequirePermission("Navigation.SettingManagement")
    public List<SettingDefinitionDto> initDefinitions() {
        return SettingDefinitionEnum.all().stream().map(def -> {
            SettingDefinitionDto dto = new SettingDefinitionDto();
            dto.setName(def.getName());
            dto.setTitle(def.getTitle());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取提供者键可选值。
     *
     * <p>
     * 根据提供者类型返回不同来源的 key：
     * </p>
     * <ul>
     * <li>G：无 providerKey</li>
     * <li>U：当前租户下用户列表</li>
     * <li>R：当前租户下角色列表</li>
     * <li>T：系统内租户列表</li>
     * </ul>
     *
     * @param providerName 提供者名称（G/U/R/T）
     * @return 可选 providerKey 列表
     */
    @RequirePermission("Navigation.SettingManagement")
    public List<ProviderKeyOptionDto> getProviderKeys(String providerName) {
        SettingProviderName provider = SettingProviderName.of(providerName);
        Long tenantId = TenantUtils.getCurrentTenantId();

        switch (provider) {
            case G:
                return List.of();
            case U:
                if (tenantId == null) {
                    throw new ValidationException("当前为宿主环境，无法获取租户下用户列表");
                }
                List<UserTenant> userTenants = userTenantRepository.findByTenantId(tenantId).stream()
                    .filter(ut -> ut != null && !Boolean.TRUE.equals(ut.getIsDeleted())).collect(Collectors.toList());
                List<Long> userIds = userTenants.stream().map(UserTenant::getUserId).filter(Objects::nonNull).distinct()
                    .collect(Collectors.toList());
                if (userIds.isEmpty()) {
                    return List.of();
                }
                List<User> users = userRepository.findAllById(userIds).stream()
                    .filter(u -> u != null && !Boolean.TRUE.equals(u.getIsDeleted())).collect(Collectors.toList());
                return users.stream()
                    .sorted(Comparator.comparing(User::getUserName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .map(u -> {
                        ProviderKeyOptionDto dto = new ProviderKeyOptionDto();
                        dto.setKey(String.valueOf(u.getId()));
                        String label = u.getUserName();
                        if (u.getName() != null && !u.getName().trim().isEmpty()) {
                            label = label + " - " + u.getName();
                        }
                        dto.setLabel(label);
                        return dto;
                    }).collect(Collectors.toList());
            case R:
                if (tenantId == null) {
                    throw new ValidationException("当前为宿主环境，无法获取租户下角色列表");
                }
                Specification<Role> roleSpec =
                    SpecificationUtils.<Role>tenantFilter().and(SpecificationUtils.<Role>notDeleted());
                List<Role> roles = roleRepository.findAll(roleSpec);
                return roles.stream()
                    .sorted(Comparator.comparing(Role::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .map(r -> {
                        ProviderKeyOptionDto dto = new ProviderKeyOptionDto();
                        dto.setKey(String.valueOf(r.getId()));
                        dto.setLabel(r.getDisplayName() != null && !r.getDisplayName().trim().isEmpty()
                            ? r.getDisplayName() : r.getName());
                        return dto;
                    }).collect(Collectors.toList());
            case T:
                Specification<Tenant> tenantSpec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
                return tenantRepository.findAll(tenantSpec).stream()
                    .sorted(Comparator.comparing(Tenant::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .map(t -> {
                        ProviderKeyOptionDto dto = new ProviderKeyOptionDto();
                        dto.setKey(String.valueOf(t.getId()));
                        dto.setLabel(t.getCode() != null && !t.getCode().trim().isEmpty()
                            ? t.getName() + " (" + t.getCode() + ")" : t.getName());
                        return dto;
                    }).collect(Collectors.toList());
            default:
                return List.of();
        }
    }

    /**
     * 按提供者优先级获取最终值（U > R > T > G）。
     *
     * @param request 请求（name）
     * @return 最终值（不存在返回 null；多角色冲突会抛异常）
     */
    @RequirePermission("Navigation.SettingManagement")
    public String getEffectiveValue(GetEffectiveValueRequest request) {
        return settingManager.getEffectiveValue(request.getName());
    }

    /**
     * 获取设置列表（分页）。
     *
     * @param request 查询条件（keyword/name/providerName/providerKey + 分页）
     * @return 分页结果
     */
    @RequirePermission("Navigation.SettingManagement")
    public PagedResult<SettingDto> getList(SettingQueryRequest request) {
        Specification<Setting> spec = buildSpecification(request);
        PageRequest pageRequest = PageRequestUtils.createDefault(request.getPageNo(), request.getPageSize());

        Page<Setting> page = settingManager.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDto));
    }

    /**
     * 创建设置。
     *
     * <p>
     * 同一 name 允许不同 provider 并存，取值按优先级覆盖。
     * </p>
     *
     * @param request 创建请求
     * @return 创建后的设置DTO
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto create(CreateSettingRequest request) {
        normalizeProvider(request);
        validateProviderForCreate(request);

        // 检查设置是否已存在（同一 name 允许不同 provider 并存，靠优先级覆盖）
        SettingProviderName provider = SettingProviderName.of(request.getProviderName());
        if (provider == SettingProviderName.G) {
            if (settingManager.existsByNameAndProvider(request.getName(), SettingProviderName.G.getCode(), null)) {
                throw new ValidationException("设置已存在(全局G): " + request.getName());
            }
        } else {
            if (settingManager.existsByNameAndProvider(request.getName(), request.getProviderName(),
                request.getProviderKey())) {
                throw new ValidationException(String.format("设置已存在(%s:%s): %s", request.getProviderName(),
                    request.getProviderKey(), request.getName()));
            }
        }

        Setting setting = new Setting();
        setting.setName(request.getName());
        setting.setValue(request.getValue());
        setting.setProviderName(request.getProviderName());
        setting.setProviderKey(request.getProviderKey());
        setting.setDescription(request.getDescription());
        setting.setTenantId(resolveTenantIdForProvider(request.getProviderName()));

        setting = settingManager.save(setting);
        log.info("Created setting: {} ({})", setting.getId(), setting.getName());
        return toDto(setting);
    }

    /**
     * 更新设置（仅允许更新 value/description）。
     *
     * @param id 设置主键
     * @param request 更新请求
     * @return 更新后的设置DTO
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto update(Long id, UpdateSettingRequest request) {
        Setting setting = settingManager.findById(id).orElseThrow(() -> new EntityNotFoundException("设置", id));

        if (request.getValue() != null) {
            setting.setValue(request.getValue());
        }

        if (request.getDescription() != null) {
            setting.setDescription(request.getDescription());
        }

        setting = settingManager.save(setting);
        log.info("Updated setting: {} ({})", setting.getId(), setting.getName());
        return toDto(setting);
    }

    /**
     * 设置值（不区分提供者）：不存在则创建，存在则更新。
     *
     * @param request name + value
     * @return 最新设置DTO
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto setValue(SetValueRequest request) {
        Setting setting = settingManager.findByName(request.getName()).orElseGet(() -> {
            Setting newSetting = new Setting();
            newSetting.setName(request.getName());
            newSetting.setTenantId(TenantUtils.getCurrentTenantId());
            return newSetting;
        });

        setting.setValue(request.getValue());
        setting = settingManager.save(setting);
        log.info("Set setting value: {} = {}", request.getName(), request.getValue());
        return toDto(setting);
    }

    /**
     * 设置值（带提供者）：按 (name+providerName+providerKey) 定位，不存在则创建，存在则更新。
     *
     * @param name 设置名称
     * @param value 设置值
     * @param providerName 提供者名称（G/U/R/T）
     * @param providerKey 提供者键（G 可为空；U/R/T 必填）
     * @return 最新设置DTO
     */
    @RequirePermission("Navigation.SettingManagement")
    public SettingDto setValue(String name, String value, String providerName, String providerKey) {
        String normalizedProviderName = normalizeText(providerName);
        String normalizedProviderKey = normalizeText(providerKey);
        validateProviderForSetValue(normalizedProviderName, normalizedProviderKey);

        Setting setting =
            settingManager.findByNameAndProvider(name, normalizedProviderName, normalizedProviderKey).orElseGet(() -> {
                Setting newSetting = new Setting();
                newSetting.setName(name);
                newSetting.setProviderName(normalizedProviderName);
                newSetting.setProviderKey(normalizedProviderKey);
                newSetting.setTenantId(resolveTenantIdForProvider(normalizedProviderName));
                return newSetting;
            });

        setting.setValue(value);
        setting = settingManager.save(setting);
        log.info("Set setting value: {} = {} (provider: {}:{})", name, value, normalizedProviderName,
            normalizedProviderKey);
        return toDto(setting);
    }

    /**
     * 删除设置。
     *
     * @param id 设置主键
     */
    @RequirePermission("Navigation.SettingManagement")
    public void delete(Long id) {
        Setting setting = settingManager.findById(id).orElseThrow(() -> new EntityNotFoundException("设置", id));

        settingManager.delete(setting);
        log.info("Deleted setting: {} ({})", setting.getId(), setting.getName());
    }

    /**
     * 按名称删除设置（不区分提供者）。
     *
     * @param name 设置名称
     */
    @RequirePermission("Navigation.SettingManagement")
    public void deleteByName(String name) {
        Setting setting = settingManager.findByName(name)
            .orElseThrow(() -> new EntityNotFoundException(String.format("设置 (名称: %s) 不存在", name)));

        settingManager.delete(setting);
        log.info("Deleted setting by name: {}", name);
    }

    /**
     * 删除指定提供者下的全部设置。
     *
     * @param providerName 提供者名称
     * @param providerKey 提供者键
     */
    @RequirePermission("Navigation.SettingManagement")
    public void deleteByProvider(String providerName, String providerKey) {
        settingManager.deleteByProvider(providerName, providerKey);
        log.info("Deleted settings by provider: {}:{}", providerName, providerKey);
    }

    /**
     * 构建查询条件
     */
    private Specification<Setting> buildSpecification(SettingQueryRequest request) {
        return SpecificationUtils.<Setting>tenantFilter().and(SpecificationUtils.<Setting>notDeleted())
            .and((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                // 关键词模糊查询（名称 + 标题/描述）
                if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                    String keyword = "%" + request.getKeyword().trim() + "%";
                    Predicate nameLike = cb.like(root.get("name"), keyword);
                    Predicate descLike = cb.like(root.get("description"), keyword);
                    predicates.add(cb.or(nameLike, descLike));
                }

                // 名称精确查询
                if (request.getName() != null && !request.getName().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("name"), request.getName().trim()));
                }

                // 提供者名称查询
                if (request.getProviderName() != null && !request.getProviderName().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("providerName"), request.getProviderName().trim()));
                }

                // 提供者键查询
                if (request.getProviderKey() != null && !request.getProviderKey().trim().isEmpty()) {
                    predicates.add(cb.equal(root.get("providerKey"), request.getProviderKey().trim()));
                }

                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
            });
    }

    /**
     * 转换为DTO
     */
    private SettingDto toDto(Setting setting) {
        SettingDto dto = new SettingDto();
        BeanUtils.copyProperties(setting, dto);
        return dto;
    }

    private void normalizeProvider(CreateSettingRequest request) {
        if (request == null) {
            return;
        }
        request.setName(normalizeText(request.getName()));
        request.setProviderName(normalizeText(request.getProviderName()));
        request.setProviderKey(normalizeText(request.getProviderKey()));
        request.setDescription(normalizeText(request.getDescription()));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateProviderForCreate(CreateSettingRequest request) {
        SettingProviderName provider = SettingProviderName.of(request.getProviderName());
        switch (provider) {
            case G:
                if (request.getProviderKey() != null) {
                    throw new ValidationException("全局(G)提供者不需要提供者键");
                }
                break;
            case U:
            case R:
            case T:
                if (request.getProviderKey() == null) {
                    throw new ValidationException("提供者键不能为空");
                }
                validateProviderKeyOwnership(provider, request.getProviderKey());
                break;
            default:
                break;
        }
    }

    private void validateProviderForSetValue(String providerName, String providerKey) {
        SettingProviderName provider = SettingProviderName.of(providerName);
        if (provider == SettingProviderName.G) {
            if (providerKey != null) {
                throw new ValidationException("全局(G)提供者不需要提供者键");
            }
            return;
        }
        if (providerKey == null) {
            throw new ValidationException("提供者键不能为空");
        }
        validateProviderKeyOwnership(provider, providerKey);
    }

    private void validateProviderKeyOwnership(SettingProviderName provider, String providerKey) {
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        if (provider == SettingProviderName.T) {
            if (currentTenantId != null && !String.valueOf(currentTenantId).equals(providerKey)) {
                throw new ValidationException("租户(T)提供者键只能为当前租户ID");
            }
            return;
        }

        if (provider == SettingProviderName.U) {
            if (currentTenantId == null) {
                throw new ValidationException("宿主环境不支持用户(U)提供者键");
            }
            Long userId = tryParseLong(providerKey);
            if (userId == null) {
                throw new ValidationException("用户(U)提供者键必须为用户ID");
            }
            if (!userTenantRepository.existsByUserIdAndTenantId(userId, currentTenantId)) {
                throw new ValidationException("用户不属于当前租户，无法作为提供者键");
            }
            return;
        }

        if (provider == SettingProviderName.R) {
            if (currentTenantId == null) {
                throw new ValidationException("宿主环境不支持角色(R)提供者键");
            }
            Long roleId = tryParseLong(providerKey);
            if (roleId == null) {
                throw new ValidationException("角色(R)提供者键必须为角色ID");
            }
            roleRepository.findById(roleId).ifPresentOrElse(role -> {
                if (role.getTenantId() == null || !role.getTenantId().equals(currentTenantId)) {
                    throw new ValidationException("角色不属于当前租户，无法作为提供者键");
                }
                if (Boolean.TRUE.equals(role.getIsDeleted())) {
                    throw new ValidationException("角色已删除，无法作为提供者键");
                }
            }, () -> {
                throw new ValidationException("角色不存在，无法作为提供者键");
            });
        }
    }

    private Long tryParseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveTenantIdForProvider(String providerName) {
        SettingProviderName provider = SettingProviderName.of(providerName);
        if (provider == SettingProviderName.G) {
            return null;
        }
        return TenantUtils.getCurrentTenantId();
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.infrastructure.repository;

import com.openhis.whale.setting.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设置Repository
 *
 * @author Whale Framework
 */
@Repository
public interface SettingRepository extends JpaRepository<Setting, Long>, JpaSpecificationExecutor<Setting> {

    /**
     * 根据名称查找设置（系统级别）
     */
    Optional<Setting> findByName(String name);

    /**
     * 根据名称和提供者查找设置
     */
    Optional<Setting> findByNameAndProviderNameAndProviderKey(
            String name, String providerName, String providerKey);

    /**
     * 根据名称和租户ID查找设置
     */
    Optional<Setting> findByNameAndTenantId(String name, Long tenantId);

    /**
     * 根据提供者查找所有设置
     */
    List<Setting> findByProviderNameAndProviderKey(String providerName, String providerKey);

    /**
     * 根据租户ID查找所有设置
     */
    List<Setting> findByTenantId(Long tenantId);

    /**
     * 检查设置是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查设置是否存在（带提供者）
     */
    boolean existsByNameAndProviderNameAndProviderKey(
            String name, String providerName, String providerKey);

    /**
     * 删除指定提供者的所有设置
     */
    void deleteByProviderNameAndProviderKey(String providerName, String providerKey);

    /**
     * 删除指定租户的所有设置
     */
    void deleteByTenantId(Long tenantId);
}

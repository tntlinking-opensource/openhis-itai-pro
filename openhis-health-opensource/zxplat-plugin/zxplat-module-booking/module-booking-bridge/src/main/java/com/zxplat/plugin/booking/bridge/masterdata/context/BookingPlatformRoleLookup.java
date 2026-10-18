/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.masterdata.context;

import com.zxplat.framework.identity.domain.Role;
import com.zxplat.framework.identity.infrastructure.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 平台角色 code → id 解析（bridge 内聚，替代 health-module-support PlatformRoleLookup）。 */
@Component
@RequiredArgsConstructor
public class BookingPlatformRoleLookup {

    private final RoleRepository roleRepository;
    private volatile Map<String, Long> builtInCodeToIdCache;

    public Long findRoleIdByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        Long cached = builtInCodeToIdMap().get(normalized);
        if (cached != null) {
            return cached;
        }
        return roleRepository.findByCode(roleCode.trim()).map(Role::getId).orElse(null);
    }

    private Map<String, Long> builtInCodeToIdMap() {
        Map<String, Long> local = builtInCodeToIdCache;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (builtInCodeToIdCache != null) {
                return builtInCodeToIdCache;
            }
            Map<String, Long> map = new HashMap<>();
            for (Role role : roleRepository.findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc()) {
                if (role == null || role.getCode() == null || role.getId() == null) {
                    continue;
                }
                map.put(role.getCode().trim().toUpperCase(Locale.ROOT), role.getId());
            }
            builtInCodeToIdCache = Map.copyOf(map);
            return builtInCodeToIdCache;
        }
    }
}

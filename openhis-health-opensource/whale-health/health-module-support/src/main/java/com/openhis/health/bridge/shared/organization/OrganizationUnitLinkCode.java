/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.bridge.shared.organization;

import com.openhis.health.domain.administration.organization.entity.Organization;

import java.util.Optional;

/**
 * HIS {@link Organization} 与 Whale {@code OrganizationUnit} 的可追溯关联键（{@code code}）规则。
 *
 * <p>两侧主键独立；同步、登录桥接、反查均只认 {@code code}，不依赖 id 对齐。</p>
 */
public final class OrganizationUnitLinkCode {

    /** OU {@code code} 列最大长度，与 {@code whale_organization_units.code} 一致。 */
    public static final int CODE_MAX_LENGTH = 128;

    private static final String ORG_ID_PREFIX = "org-";

    private OrganizationUnitLinkCode() {
    }

    /**
     * 由组织快照生成 OU 关联 code：优先 {@code busNo}，否则 {@code org-{adm_organization.id}}。
     */
    public static String fromOrganization(Organization organization) {
        if (organization == null) {
            return null;
        }
        if (organization.getBusNo() == null || organization.getBusNo().isBlank()) {
            if (organization.getId() == null) {
                return null;
            }
            return ORG_ID_PREFIX + organization.getId();
        }
        String busNo = organization.getBusNo().trim();
        if (busNo.length() <= CODE_MAX_LENGTH) {
            return busNo;
        }
        return busNo.substring(0, CODE_MAX_LENGTH);
    }

    /**
     * 由 OU {@code code} 反查 HIS 组织主键（仅用于桥接，业务侧仍应使用会话中的组织 id）。
     */
    public static Optional<Long> parseBusinessOrganizationId(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        if (trimmed.startsWith(ORG_ID_PREFIX)) {
            try {
                return Optional.of(Long.parseLong(trimmed.substring(ORG_ID_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

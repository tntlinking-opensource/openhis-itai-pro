/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.organization;

import com.zxplat.domain.administration.organization.entity.Organization;

import java.util.Optional;

/**
 * HIS {@link Organization} 与 ZXPlat {@code OrganizationUnit} 的可追溯关联键（{@code code}）规则。
 *
 * <p>两侧主键独立；同步、登录桥接、反查均只认 {@code code}，不依赖 id 对齐。</p>
 */
public final class OrganizationUnitLinkCode {

    /** OU {@code code} 列最大长度，与 {@code zxplat_organization_units.code} 一致。 */
    public static final int CODE_MAX_LENGTH = 128;

    private static final String ORG_ID_PREFIX = "org-";

    private OrganizationUnitLinkCode() {
    }

    /**
     * 由组织快照生成 OU 关联 code：优先 {@code busNo}，否则 {@code org-{adm_organization.id}}。
     *
     * @param organization HIS 组织实体
     * @return OU code；无法生成时 null
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
     *
     * @param code ZXPlat OU code
     * @return 解析出的 {@code adm_organization.id}；非 org- 前缀或非法时 empty
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

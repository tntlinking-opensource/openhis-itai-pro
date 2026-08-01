/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.catalog;

import com.openhis.chs.domain.share.enums.ValiFlag;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceDiseaseCatalogRow;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceDiseaseCatalogSourceExtension;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从 {@code yb_national_catalog_item} 按 list_type 读取疾病/诊断类目录。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceDiseaseCatalogSourceExtensionImpl implements InsuranceDiseaseCatalogSourceExtension {

    /**
     * 诊断目录导入读取本地镜像。
     * <p>不按 vali_flag 过滤：部分目录（如 1310）官方包整表为 0（旧版数据），
     * 仍需可灌入院内诊断目录；字面量 {@code null} 视为无效字段。</p>
     */
    private static final String SQL = """
        SELECT DISTINCT ON (hilist_code)
               hilist_code, hilist_name, pinyin
          FROM yb_national_catalog_item
         WHERE is_deleted = false
           AND list_type = ?
           AND (tenant_id IS NULL OR tenant_id = ?)
           AND hilist_code IS NOT NULL
           AND btrim(hilist_code) <> ''
           AND lower(btrim(hilist_code)) <> 'null'
           AND hilist_name IS NOT NULL
           AND btrim(hilist_name) <> ''
           AND lower(btrim(hilist_name)) <> 'null'
         ORDER BY hilist_code,
                  CASE WHEN vali_flag = ? THEN 0 ELSE 1 END,
                  updated_at DESC NULLS LAST,
                  id DESC
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<InsuranceDiseaseCatalogRow> listActiveRows(String listType) {
        if (!StringUtils.hasText(listType)) {
            return Collections.emptyList();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        Long finalTenantId = tenantId;
        String type = listType.trim();
        return jdbcTemplate.query(
            SQL,
            rs -> {
                List<InsuranceDiseaseCatalogRow> rows = new ArrayList<>();
                while (rs.next()) {
                    InsuranceDiseaseCatalogRow row = new InsuranceDiseaseCatalogRow();
                    row.setHilistCode(rs.getString("hilist_code"));
                    row.setHilistName(rs.getString("hilist_name"));
                    row.setPinyin(rs.getString("pinyin"));
                    rows.add(row);
                }
                return rows;
            },
            type,
            finalTenantId,
            ValiFlag.VALID.getCode());
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.catalog;

import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.ValiFlag;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogRow;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogSourceExtension;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code yb_national_catalog_item}（list_type=1308）读取手术操作目录。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceSurgeryCatalogSourceExtensionImpl implements InsuranceSurgeryCatalogSourceExtension {

    private static final String SQL = """
        SELECT DISTINCT ON (hilist_code)
               hilist_code, hilist_name, pinyin
          FROM yb_national_catalog_item
         WHERE is_deleted = false
           AND list_type = ?
           AND (vali_flag IS NULL OR vali_flag = ?)
           AND (tenant_id IS NULL OR tenant_id = ?)
           AND hilist_code IS NOT NULL
           AND btrim(hilist_code) <> ''
           AND lower(btrim(hilist_code)) <> 'null'
           AND hilist_name IS NOT NULL
           AND btrim(hilist_name) <> ''
           AND lower(btrim(hilist_name)) <> 'null'
         ORDER BY hilist_code, updated_at DESC NULLS LAST, id DESC
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<InsuranceSurgeryCatalogRow> listActiveSurgeryRows() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        Long finalTenantId = tenantId;
        return jdbcTemplate.query(
            SQL,
            rs -> {
                List<InsuranceSurgeryCatalogRow> rows = new ArrayList<>();
                while (rs.next()) {
                    InsuranceSurgeryCatalogRow row = new InsuranceSurgeryCatalogRow();
                    row.setHilistCode(rs.getString("hilist_code"));
                    row.setHilistName(rs.getString("hilist_name"));
                    row.setPinyin(rs.getString("pinyin"));
                    rows.add(row);
                }
                return rows;
            },
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            ValiFlag.VALID.getCode(),
            finalTenantId);
    }
}

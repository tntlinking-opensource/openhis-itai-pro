/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.catalog;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.enums.ValiFlag;
import com.zxplat.domain.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogRow;
import com.zxplat.domain.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogSourceExtension;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code yb_national_catalog_item}（list_type=1308）读取手术操作目录。
 *
 * <p>主码取 hilist（第 11/12 列）；临床版码/名在 SQL 中从 {@code raw_json} 第 16/17 列抽出，避免把整段 JSON 拉到 JVM。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InsuranceSurgeryCatalogSourceExtensionImpl implements InsuranceSurgeryCatalogSourceExtension {

    /** JDBC 流式拉取，降低 1 万+ 行一次性缓冲。 */
    private static final int FETCH_SIZE = 1000;

    /**
     * DISTINCT ON 去重后只投影业务列；{@code raw_json LIKE '[%'} 时才 ::json，降低坏行整句失败概率。
     */
    private static final String SQL = """
        SELECT DISTINCT ON (hilist_code)
               hilist_code,
               hilist_name,
               pinyin,
               CASE WHEN raw_json LIKE '[%'
                    THEN CASE
                           WHEN lower(btrim(coalesce(raw_json::json ->> 15, ''))) IN ('', 'null', '--')
                           THEN NULL
                           ELSE btrim(raw_json::json ->> 15)
                         END
                    ELSE NULL END AS clinic_code,
               CASE WHEN raw_json LIKE '[%'
                    THEN CASE
                           WHEN lower(btrim(coalesce(raw_json::json ->> 16, ''))) IN ('', 'null', '--')
                           THEN NULL
                           ELSE btrim(raw_json::json ->> 16)
                         END
                    ELSE NULL END AS clinic_name
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

    /**
     * CHS 模块已装配即视为可导入。
     *
     * @return 恒为 true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 列出有效 1308 行，并带出临床版码/名（可能为空）。
     *
     * @return 手术操作镜像行
     */
    @Override
    @Transactional(readOnly = true)
    public List<InsuranceSurgeryCatalogRow> listActiveSurgeryRows() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        Long finalTenantId = tenantId;
        return jdbcTemplate.query(
            con -> {
                PreparedStatement ps = con.prepareStatement(SQL);
                ps.setFetchSize(FETCH_SIZE);
                ps.setString(1, ChsInfNo.SURGERY_CATALOG_DOWNLOAD);
                ps.setString(2, ValiFlag.VALID.getCode());
                ps.setLong(3, finalTenantId);
                return ps;
            },
            rs -> {
                List<InsuranceSurgeryCatalogRow> rows = new ArrayList<>();
                while (rs.next()) {
                    InsuranceSurgeryCatalogRow row = new InsuranceSurgeryCatalogRow();
                    row.setHilistCode(rs.getString("hilist_code"));
                    row.setHilistName(rs.getString("hilist_name"));
                    row.setPinyin(rs.getString("pinyin"));
                    row.setClinicCode(trimToNull(rs.getString("clinic_code")));
                    row.setClinicName(trimToNull(rs.getString("clinic_name")));
                    rows.add(row);
                }
                return rows;
            });
    }
}

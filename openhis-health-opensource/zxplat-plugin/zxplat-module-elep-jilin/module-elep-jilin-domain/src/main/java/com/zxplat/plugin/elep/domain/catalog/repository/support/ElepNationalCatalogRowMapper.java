/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.catalog.repository.support;

import com.zxplat.plugin.elep.domain.catalog.model.ElepNationalCatalogRow;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class ElepNationalCatalogRowMapper implements RowMapper<ElepNationalCatalogRow> {

    public static final ElepNationalCatalogRowMapper INSTANCE = new ElepNationalCatalogRowMapper();

    private ElepNationalCatalogRowMapper() {
    }

    @Override
    public ElepNationalCatalogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ElepNationalCatalogRow()
                .setHilistCode(rs.getString("hilist_code"))
                .setHilistName(rs.getString("hilist_name"))
                .setSpec(rs.getString("spec"))
                .setDosform(rs.getString("dosform"))
                .setAprvno(rs.getString("aprvno"))
                .setProdentpName(rs.getString("prodentp_name"))
                .setPinyin(rs.getString("pinyin"))
                .setValiFlag(rs.getString("vali_flag"))
                .setSourceInfno(rs.getString("source_infno"))
                .setListType(rs.getString("list_type"));
    }
}

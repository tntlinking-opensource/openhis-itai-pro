/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.repository.support;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@link YbNationalCatalogItem} JDBC 行映射。 */
public final class YbNationalCatalogItemRowMapper {

    public static final RowMapper<YbNationalCatalogItem> INSTANCE = new YbNationalCatalogItemRowMapper()::mapRow;

    private YbNationalCatalogItemRowMapper() {
    }

    private YbNationalCatalogItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        YbNationalCatalogItem row = new YbNationalCatalogItem();
        row.setId(rs.getLong("id"));
        row.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        row.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        row.setCreatedBy(rs.getObject("created_by", Long.class));
        row.setUpdatedBy(rs.getObject("updated_by", Long.class));
        row.setIsDeleted(rs.getBoolean("is_deleted"));
        row.setDeletedAt(toLocalDateTime(rs.getTimestamp("deleted_at")));
        row.setDeletedBy(rs.getObject("deleted_by", Long.class));
        row.setTenantId(rs.getObject("tenant_id", Long.class));
        row.setContractId(rs.getLong("contract_id"));
        row.setListType(rs.getString("list_type"));
        row.setHilistCode(rs.getString("hilist_code"));
        row.setHilistName(rs.getString("hilist_name"));
        row.setChrgitmLv(rs.getString("chrgitm_lv"));
        row.setLmtUsedFlag(rs.getString("lmt_used_flag"));
        row.setBegndate(toLocalDate(rs.getDate("begndate")));
        row.setEnddate(toLocalDate(rs.getDate("enddate")));
        row.setValiFlag(rs.getString("vali_flag"));
        row.setSpec(rs.getString("spec"));
        row.setDosform(rs.getString("dosform"));
        row.setAprvno(rs.getString("aprvno"));
        row.setProdentpName(rs.getString("prodentp_name"));
        row.setPinyin(rs.getString("pinyin"));
        row.setRawJson(rs.getString("raw_json"));
        row.setSourceInfno(rs.getString("source_infno"));
        row.setUpdtTime(toLocalDateTime(rs.getTimestamp("updt_time")));
        row.setSourceFileQuryNo(rs.getString("source_file_qury_no"));
        row.setSourceLineNo(rs.getObject("source_line_no", Integer.class));
        return row;
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        if (value == null) {
            return null;
        }
        return value.toLocalDateTime();
    }

    private static LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        return value.toLocalDate();
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.repository.support;

import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * {@link YbInvUpload} JdbcTemplate 行映射。
 */
public final class YbInvUploadRowMapper implements RowMapper<YbInvUpload> {

    public static final YbInvUploadRowMapper INSTANCE = new YbInvUploadRowMapper();

    private YbInvUploadRowMapper() {
    }

    @Override
    public YbInvUpload mapRow(ResultSet rs, int rowNum) throws SQLException {
        YbInvUpload row = new YbInvUpload();
        row.setId(rs.getLong("id"));
        row.setTenantId(rs.getLong("tenant_id"));
        row.setContractId(rs.getLong("contract_id"));
        row.setContractBusNo(rs.getString("contract_bus_no"));
        row.setInfno(rs.getString("infno"));
        row.setInvDataType(rs.getString("inv_data_type"));
        row.setFixmedinsBchno(rs.getString("fixmedins_bchno"));
        row.setMedListCodg(rs.getString("med_list_codg"));
        row.setSourceType(rs.getString("source_type"));
        long sourceId = rs.getLong("source_id");
        if (!rs.wasNull()) {
            row.setSourceId(sourceId);
        }
        long sourceLineId = rs.getLong("source_line_id");
        if (!rs.wasNull()) {
            row.setSourceLineId(sourceLineId);
        }
        row.setUploadStatus(rs.getString("upload_status"));
        row.setPlatformRetRslt(rs.getString("platform_ret_rslt"));
        row.setPlatformMsg(rs.getString("platform_msg"));
        row.setMsgid(rs.getString("msgid"));
        row.setRequestJson(rs.getString("request_json"));
        row.setResponseJson(rs.getString("response_json"));
        row.setRetryCount(rs.getInt("retry_count"));
        Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
        if (nextRetryAt != null) {
            row.setNextRetryAt(nextRetryAt.toLocalDateTime());
        }
        long operatorId = rs.getLong("operator_id");
        if (!rs.wasNull()) {
            row.setOperatorId(operatorId);
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            row.setCreatedAt(createdAt.toLocalDateTime());
        }
        return row;
    }
}

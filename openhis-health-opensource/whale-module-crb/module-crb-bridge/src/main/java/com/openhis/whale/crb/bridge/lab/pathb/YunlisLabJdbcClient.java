/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.crb.bridge.lab.pathb.YunlisDataSourceConfig.YUNLIS_JDBC_TEMPLATE;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_BATCH_SIZE;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.OUTBOX_PENDING;

/**
 * yunlis JDBC 读客户端：Outbox / l_laborder / l_testresult。
 */
@Component
@ConditionalOnProperty(name = "openhis.crb.lab.yunlis.enabled", havingValue = "true")
public class YunlisLabJdbcClient {

    private static final String SQL_PENDING_OUTBOX =
            "SELECT id, l_laborder_id FROM crb_lab_outbox "
                    + "WHERE process_status = ? ORDER BY id ASC LIMIT ?";

    private static final String SQL_MARK_OUTBOX =
            "UPDATE crb_lab_outbox SET process_status = ?, process_message = ?, processed_at = NOW() "
                    + "WHERE id = ?";

    private static final String SQL_LAB_ORDER_BY_ID =
            "SELECT o.L_LABORDER_ID, o.BARCODE, o.APPLYNO, o.SAMPLENO, o.TYPE, o.RECEIVEFLAG, "
                    + "o.PATIENTID, o.PATIENTBH, o.PATIENTNAME, o.SHENFENZHENGHAO, o.BED, o.SHENQINGBQ, "
                    + "o.SPECIMEN, s.SAMPLEDESCRIBE, o.APPLYSCODE, o.APPLYSECTION, "
                    + "o.REQUESTER, o.REQUENAME, o.QBGSJ, o.RECEIVETIME, o.EXECUTETIME, o.CHECKTIME, "
                    + "o.SHZ, o.EXECUTOR, o.ZXKSBM, o.LABDEPARTMENT, o.BEIZHU "
                    + "FROM l_laborder o "
                    + "LEFT JOIN l_sampletype s ON s.SAMPLETYPE = o.SPECIMEN "
                    + "WHERE o.L_LABORDER_ID = ?";

    private static final String SQL_LAB_ORDER_BY_SAMPLE =
            "SELECT o.L_LABORDER_ID, o.BARCODE, o.APPLYNO, o.SAMPLENO, o.TYPE, o.RECEIVEFLAG, "
                    + "o.PATIENTID, o.PATIENTBH, o.PATIENTNAME, o.SHENFENZHENGHAO, o.BED, o.SHENQINGBQ, "
                    + "o.SPECIMEN, s.SAMPLEDESCRIBE, o.APPLYSCODE, o.APPLYSECTION, "
                    + "o.REQUESTER, o.REQUENAME, o.QBGSJ, o.RECEIVETIME, o.EXECUTETIME, o.CHECKTIME, "
                    + "o.SHZ, o.EXECUTOR, o.ZXKSBM, o.LABDEPARTMENT, o.BEIZHU "
                    + "FROM l_laborder o "
                    + "LEFT JOIN l_sampletype s ON s.SAMPLETYPE = o.SPECIMEN "
                    + "WHERE o.SAMPLENO = ? ORDER BY o.L_LABORDER_ID DESC LIMIT 1";

    private static final String SQL_TEST_RESULTS_BY_SAMPLE =
            "SELECT t.L_TESTRESULT_ID, t.TESTID, t.TESTRESULT, t.UNIT, t.REFLO, t.REFHI, "
                    + "t.RESULTTIP, t.SAMPLENO, d.CHINESENAME "
                    + "FROM l_testresult t "
                    + "LEFT JOIN l_testdescribe d ON d.TESTID = t.TESTID "
                    + "WHERE t.SAMPLENO = ?";

    private static final String SQL_TEST_RESULT_BY_ID =
            "SELECT t.L_TESTRESULT_ID, t.TESTID, t.TESTRESULT, t.UNIT, t.REFLO, t.REFHI, "
                    + "t.RESULTTIP, t.SAMPLENO, d.CHINESENAME "
                    + "FROM l_testresult t "
                    + "LEFT JOIN l_testdescribe d ON d.TESTID = t.TESTID "
                    + "WHERE t.L_TESTRESULT_ID = ?";

    private final JdbcTemplate yunlisJdbcTemplate;

    private static final RowMapper<YunlisOutboxRow> OUTBOX_MAPPER = (rs, rowNum) -> {
        YunlisOutboxRow row = new YunlisOutboxRow();
        row.setId(rs.getLong("id"));
        row.setLabOrderId(rs.getLong("l_laborder_id"));
        return row;
    };

    private static final RowMapper<YunlisLabOrderRow> LAB_ORDER_MAPPER = YunlisLabJdbcClient::mapLabOrder;

    private static final RowMapper<YunlisTestResultRow> TEST_RESULT_MAPPER = YunlisLabJdbcClient::mapTestResult;

    /**
     * @param yunlisJdbcTemplate yunlis 专用 JdbcTemplate
     */
    public YunlisLabJdbcClient(@Qualifier(YUNLIS_JDBC_TEMPLATE) JdbcTemplate yunlisJdbcTemplate) {
        this.yunlisJdbcTemplate = yunlisJdbcTemplate;
    }

    /**
     * 拉取待处理 Outbox 批次。
     *
     * @return 待处理行
     */
    public List<YunlisOutboxRow> listPendingOutbox() {
        return yunlisJdbcTemplate.query(SQL_PENDING_OUTBOX, OUTBOX_MAPPER, OUTBOX_PENDING, OUTBOX_BATCH_SIZE);
    }

    /**
     * 回写 Outbox 处理结果。
     *
     * @param outboxId Outbox id
     * @param status 状态码
     * @param message 说明（可空）
     */
    public void markOutbox(Long outboxId, String status, String message) {
        yunlisJdbcTemplate.update(SQL_MARK_OUTBOX, status, message, outboxId);
    }

    /**
     * 按主键加载检验报告头。
     *
     * @param labOrderId L_LABORDER_ID
     * @return 行；不存在则 empty
     */
    public Optional<YunlisLabOrderRow> findLabOrderById(Long labOrderId) {
        if (labOrderId == null) {
            return Optional.empty();
        }
        List<YunlisLabOrderRow> rows =
                yunlisJdbcTemplate.query(SQL_LAB_ORDER_BY_ID, LAB_ORDER_MAPPER, labOrderId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 按标本号加载最近一条报告头（明细反查用）。
     *
     * @param sampleNo SAMPLENO
     * @return 行；不存在则 empty
     */
    public Optional<YunlisLabOrderRow> findLabOrderBySampleNo(String sampleNo) {
        if (sampleNo == null || sampleNo.isBlank()) {
            return Optional.empty();
        }
        List<YunlisLabOrderRow> rows =
                yunlisJdbcTemplate.query(SQL_LAB_ORDER_BY_SAMPLE, LAB_ORDER_MAPPER, sampleNo);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 按标本号加载全部检验明细。
     *
     * @param sampleNo SAMPLENO
     * @return 明细列表
     */
    public List<YunlisTestResultRow> listTestResultsBySampleNo(String sampleNo) {
        if (sampleNo == null || sampleNo.isBlank()) {
            return List.of();
        }
        return yunlisJdbcTemplate.query(SQL_TEST_RESULTS_BY_SAMPLE, TEST_RESULT_MAPPER, sampleNo);
    }

    /**
     * 按主键加载单条检验明细。
     *
     * @param testResultId L_TESTRESULT_ID
     * @return 行；不存在则 empty
     */
    public Optional<YunlisTestResultRow> findTestResultById(Long testResultId) {
        if (testResultId == null) {
            return Optional.empty();
        }
        List<YunlisTestResultRow> rows =
                yunlisJdbcTemplate.query(SQL_TEST_RESULT_BY_ID, TEST_RESULT_MAPPER, testResultId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private static YunlisLabOrderRow mapLabOrder(ResultSet rs, int rowNum) throws SQLException {
        YunlisLabOrderRow row = new YunlisLabOrderRow();
        row.setLabOrderId(rs.getLong("L_LABORDER_ID"));
        row.setBarcode(rs.getString("BARCODE"));
        row.setApplyNo(rs.getString("APPLYNO"));
        row.setSampleNo(rs.getString("SAMPLENO"));
        row.setType(rs.getString("TYPE"));
        row.setReceiveFlag(rs.getString("RECEIVEFLAG"));
        row.setPatientId(rs.getString("PATIENTID"));
        row.setPatientBh(rs.getString("PATIENTBH"));
        row.setPatientName(rs.getString("PATIENTNAME"));
        row.setIdCard(rs.getString("SHENFENZHENGHAO"));
        row.setBed(rs.getString("BED"));
        row.setWardName(rs.getString("SHENQINGBQ"));
        row.setSpecimen(rs.getString("SPECIMEN"));
        row.setSpecimenName(rs.getString("SAMPLEDESCRIBE"));
        row.setApplyDeptCode(rs.getString("APPLYSCODE"));
        row.setApplyDeptName(rs.getString("APPLYSECTION"));
        row.setRequester(rs.getString("REQUESTER"));
        row.setRequesterName(rs.getString("REQUENAME"));
        row.setSamplingTime(toLocalDateTime(rs.getTimestamp("QBGSJ")));
        row.setReceiveTime(toLocalDateTime(rs.getTimestamp("RECEIVETIME")));
        row.setExecuteTime(toLocalDateTime(rs.getTimestamp("EXECUTETIME")));
        row.setCheckTime(toLocalDateTime(rs.getTimestamp("CHECKTIME")));
        row.setAuditor(rs.getString("SHZ"));
        row.setExecutor(rs.getString("EXECUTOR"));
        row.setExecDeptCode(rs.getString("ZXKSBM"));
        row.setLabDepartment(rs.getString("LABDEPARTMENT"));
        row.setRemark(rs.getString("BEIZHU"));
        return row;
    }

    private static YunlisTestResultRow mapTestResult(ResultSet rs, int rowNum) throws SQLException {
        YunlisTestResultRow row = new YunlisTestResultRow();
        row.setTestResultId(rs.getLong("L_TESTRESULT_ID"));
        row.setTestId(rs.getString("TESTID"));
        row.setTestResult(rs.getString("TESTRESULT"));
        row.setUnit(rs.getString("UNIT"));
        row.setRefLo(rs.getString("REFLO"));
        row.setRefHi(rs.getString("REFHI"));
        row.setResultTip(rs.getString("RESULTTIP"));
        row.setSampleNo(rs.getString("SAMPLENO"));
        row.setChineseName(rs.getString("CHINESENAME"));
        return row;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime();
    }
}

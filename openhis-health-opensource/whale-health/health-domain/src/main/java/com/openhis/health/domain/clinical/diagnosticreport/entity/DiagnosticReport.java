/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.diagnosticreport.entity;

import com.openhis.health.domain.share.constants.DiagnosticReportStatusConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 临床总检报告聚合根（表 {@code cln_diagnostic_report}，DM-CLN-008）。
 *
 * <p>1 PE Encounter 对应 1 主报告；与 LIS/PACS 报告隔离，禁止混用 lis 包类型。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cln_diagnostic_report")
public class DiagnosticReport extends BaseAuditedEntity {

    /** 院区/机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 就诊标识（业务唯一）。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 患者标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 状态枚举（preliminary/final/…）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 综述文本。 */
    @Column(name = "summary_text")
    private String summaryText;

    /** 总检结论文本。 */
    @Column(name = "conclusion_text")
    private String conclusionText;

    /** 结果引用 JSON（Observation id 列表）。 */
    @Column(name = "result_refs_json")
    private String resultRefsJson;

    /** PDF 存储键（REP 回写）。 */
    @Column(name = "pdf_storage_key")
    private String pdfStorageKey;

    /** 定稿时间。 */
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    /** 定稿人标识。 */
    @Column(name = "finalized_by")
    private Long finalizedBy;

    /** 打印次数。 */
    @Column(name = "print_count")
    private Integer printCount;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 初始化为 preliminary 草稿行。
     */
    public void markPreliminary() {
        this.statusEnum = DiagnosticReportStatusConstants.STATUS_PRELIMINARY;
    }

    /**
     * 保存综述/结论草稿（BR-CLN-DR-02：final 后拒绝）。
     *
     * @param summary    综述
     * @param conclusion 结论
     * @param resultRefs 结果引用 JSON
     */
    public void saveDraft(String summary, String conclusion, String resultRefs) {
        if (DiagnosticReportStatusConstants.STATUS_FINAL.equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MASTER_ALREADY_FINAL, "总检报告已定稿，不可再改草稿");
        }
        boolean summaryEmpty = StringUtils.isEmpty(summary);
        boolean conclusionEmpty = StringUtils.isEmpty(conclusion);
        if (summaryEmpty && conclusionEmpty) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "综述与结论不能同时为空");
        }
        if (!summaryEmpty) {
            this.summaryText = summary;
        }
        if (!conclusionEmpty) {
            this.conclusionText = conclusion;
        }
        if (resultRefs != null) {
            this.resultRefsJson = resultRefs;
        }
        if (StringUtils.isEmpty(this.statusEnum)) {
            this.statusEnum = DiagnosticReportStatusConstants.STATUS_PRELIMINARY;
        }
    }

    /**
     * 定稿为 final（BR-CLN-DR-02）。
     *
     * @param operatorId 定稿人
     */
    public void finalizeReport(Long operatorId) {
        if (DiagnosticReportStatusConstants.STATUS_FINAL.equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MASTER_ALREADY_FINAL, "总检报告已定稿");
        }
        this.statusEnum = DiagnosticReportStatusConstants.STATUS_FINAL;
        this.finalizedAt = LocalDateTime.now();
        this.finalizedBy = operatorId;
    }

    /**
     * 累加打印次数（REP-03；null 视为 0）。
     *
     * @param increaseBy 增量，须大于 0
     */
    public void increasePrintCount(int increaseBy) {
        if (increaseBy <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.DIAGNOSTIC_REPORT_INVALID_ARGUMENT, "打印次数增量必须大于 0");
        }
        int origin = printCount == null ? 0 : printCount;
        this.printCount = origin + increaseBy;
    }
}

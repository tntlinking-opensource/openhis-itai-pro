/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 医保文件通知明细行（{@code yb_catalog_notice_line}）。
 * <p>大批量入库（数千行）实现 {@link IExcludeFromAudit}，避免逐行异步审计打满线程池导致入库失败。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_notice_line")
public class YbCatalogNoticeLine extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 所属文件头主键。 */
    @Column(name = "doc_id", nullable = false)
    private Long docId;

    /** 动作：ADD/MODIFY/STOP/PROVINCE_MODIFY/SET_PAY_CLASS/UNKNOWN。 */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    /** 国标目录类别（101/201/301…）。 */
    @Column(name = "list_type", length = 30)
    private String listType;

    /** 国家/医疗目录编码（选码主键；可空）。 */
    @Column(name = "hilist_code", length = 64)
    private String hilistCode;

    /** 物价/项目代码（支付对照主键 01* 等）。 */
    @Column(name = "project_code", length = 64)
    private String projectCode;

    /** 名称。 */
    @Column(name = "hilist_name", length = 512)
    private String hilistName;

    /** 行级生效起日。 */
    @Column(name = "begin_date")
    private LocalDate beginDate;

    /** 行级终止日；STOP 强烈建议有值。 */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 本期变更字段说明。 */
    @Column(name = "change_fields", length = 512)
    private String changeFields;

    /** 收费项目等级：1 甲 / 2 乙 / 3 自费。 */
    @Column(name = "chrgitm_lv", length = 8)
    private String chrgitmLv;

    /** 限制使用标志。 */
    @Column(name = "lmt_used_flag", length = 8)
    private String lmtUsedFlag;

    /** 限制使用范围。 */
    @Column(name = "lmt_scope", length = 512)
    private String lmtScope;

    /** 医保目录定价上限金额；空或≤0 表示无限价。 */
    @Column(name = "price_limit_amt", precision = 18, scale = 4)
    private BigDecimal priceLimitAmt;

    /** 医保目录超限处理方式（通知列原值）。 */
    @Column(name = "overlmt_dspo_way", length = 8)
    private String overlmtDspoWay;

    /** 双通道标志等扩展。 */
    @Column(name = "dual_channel_flag", length = 8)
    private String dualChannelFlag;

    /** 整行规范化 JSON（防列丢失）。 */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** 来源 sheet 名。 */
    @Column(name = "sheet_name", length = 128)
    private String sheetName;

    /** 原表行号。 */
    @Column(name = "row_no")
    private Integer rowNo;

    /** 与镜像对照：HIT/MISS/AMBIGUOUS。 */
    @Column(name = "match_status", length = 32)
    private String matchStatus;

    /** 应用结果：SKIPPED/PATCHED/CREATED/FAILED… */
    @Column(name = "apply_result", length = 32)
    private String applyResult;

    /** 行状态：ACTIVE/SUPERSEDED。 */
    @Column(name = "line_status", nullable = false, length = 32)
    private String lineStatus = "ACTIVE";

    /** 行级警告（PARTIAL 入库时保留）。 */
    @Column(name = "warning_message", length = 1024)
    private String warningMessage;
}

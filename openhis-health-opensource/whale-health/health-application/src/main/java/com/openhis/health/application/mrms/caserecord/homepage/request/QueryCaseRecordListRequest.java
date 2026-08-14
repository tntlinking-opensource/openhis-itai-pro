/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 病案检索分页请求（IF-MR-List）。
 */
@Getter
@Setter
public class QueryCaseRecordListRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 状态码过滤（单值，与 statusCodes 二选一）。 */
    private String statusCode;

    /** 状态码过滤（多值 IN，优先于 statusCode）。 */
    private List<String> statusCodes;

    /** 入院科别名称过滤。 */
    private String admCatyName;

    /** 主治医师 {@code adm_practitioner.id} 过滤。 */
    private Long attendingPractitionerId;

    /**
     * 入院日期起（含）；与 {@link #admTo} 组成闭区间，下推就诊 {@code startTime}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admFrom;

    /**
     * 入院日期止（含）；语义同 {@link #admFrom}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate admTo;

    /**
     * 出院日期起（含）；与 {@link #dscgTo} 组成闭区间，下推就诊 {@code endTime}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate dscgFrom;

    /**
     * 出院日期止（含）；语义同 {@link #dscgFrom}。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    private LocalDate dscgTo;

    /**
     * 是否出院（映射住院就诊状态并集）：{@code "1"}=已出院态（COMPLETED/DISCHARGED），
     * {@code "0"}=未出院态（IN_PROGRESS/ON_HOLD/PENDING）；空/全部用默认在院+出院并集。
     */
    private String dischargedCode;

    /**
     * 4401 首页上传状态过滤（与主档 {@code chs_upload_status_code} 对齐）。
     * {@code none}=未上传（空或 null）；{@code pending}/{@code success}/{@code failed}/{@code skipped} 精确匹配。
     */
    private String chsUploadStatusCode;

    /**
     * 前端首页上传状态别名（与 {@link #chsUploadStatusCode} 同口径；二者择一，优先 chsUploadStatusCode）。
     */
    private String homepageUploadStatus;

    /**
     * 结算上传状态过滤（与首页同口径：none/pending/success/failed/skipped）。
     * 住院就诊列表路径本页内存过滤；结算上传列未落库时仅 {@code none} 可命中。
     */
    private String settleUploadStatus;

    /**
     * 是否已结算：{@code "1"}/{@code "0"}。
     * <p>住院就诊列表路径：与出院态求交后下推 status_enum（已结=COMPLETED/DISCHARGED）。
     */
    private String settledCode;
}

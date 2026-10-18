/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病案列表分页查询入参（DM-MRMS-001 §6）。
 *
 * <p>分页与关键字见基类 {@link AbstractPageQuery#getPageNo()}、{@link AbstractPageQuery#getKeyword()}。</p>
 */
@Getter
@Setter
public class CaseRecordPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 租户主键 {@code tenant_id}，条件下推必填。 */
    private Long tenantId;

    /** 病案状态单值过滤（与 {@link #statusCodes} 二选一，存 {@link com.zxplat.domain.share.enums.CaseRecordStatus} code）。 */
    private String statusCode;

    /** 病案状态多值 IN 过滤（优先级高于 {@link #statusCode}）。 */
    private List<String> statusCodes;

    /** 入院科室名称精确匹配（主档冗余 {@code adm_caty_name}）。 */
    private String admCatyName;

    /** 主治医师 {@code adm_practitioner.id}（主档冗余 {@code atddr_practitioner_id}）。 */
    private Long attendingPractitionerId;

    /** 入院时间下限（含，主档冗余 {@code adm_time}）。 */
    private LocalDateTime admFrom;

    /** 入院时间上限（含，主档冗余 {@code adm_time}）。 */
    private LocalDateTime admTo;

    /** 出院时间下限（含，主档冗余 {@code dscg_time}）。 */
    private LocalDateTime dscgFrom;

    /** 出院时间上限（含，主档冗余 {@code dscg_time}）。 */
    private LocalDateTime dscgTo;

    /**
     * 是否出院：{@code "1"}={@code dscg_time} 非空，{@code "0"}={@code dscg_time} 为空；空则不过滤。
     */
    private String dischargedCode;

    /**
     * 4401 上传状态过滤（主档 {@code chs_upload_status_code}）。
     * {@code none}=未上传（列 null 或空串）；其余值精确匹配。
     */
    private String chsUploadStatusCode;

    /**
     * 结算上传状态（二期占位：本期 QueryService 接收但不下推过滤）。
     */
    private String settleUploadStatus;

    /**
     * 是否已结算（二期占位：本期 QueryService 接收但不下推过滤）。
     */
    private String settledCode;
}

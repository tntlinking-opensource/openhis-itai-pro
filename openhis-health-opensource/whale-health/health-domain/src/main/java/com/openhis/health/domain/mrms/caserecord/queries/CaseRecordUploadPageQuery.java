/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 4401 上传待传列表分页查询入参。
 *
 * <p>仅 {@link com.openhis.health.domain.share.enums.CaseRecordStatus#ARCHIVED} 病案可上传；
 * 关键字见基类 {@link AbstractPageQuery#getKeyword()}（姓名/住院号/病案号）。</p>
 */
@Getter
@Setter
public class CaseRecordUploadPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 租户主键 {@code tenant_id}，条件下推必填。 */
    private Long tenantId;

    /** CHS 上传状态 code；{@code pending} 表示待传（含 null/pending/failed 可重试）。 */
    private String chsUploadStatusCode;
}

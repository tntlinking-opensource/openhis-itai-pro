/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.borrowrequest.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 借阅申请分页查询入参。
 *
 * <p>关键字见基类 {@link AbstractPageQuery#getKeyword()}（申请人/病案号等，由应用层 Specification 解释）。</p>
 */
@Getter
@Setter
public class BorrowRequestPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 租户主键 {@code tenant_id}，条件下推必填。 */
    private Long tenantId;

    /** 借阅工作流状态 code（{@link com.openhis.health.domain.share.enums.BorrowRequestStatus}）。 */
    private String statusCode;

    /** 按病案主档 {@code mrms_case_record.id} 精确过滤。 */
    private Long caseRecordId;
}

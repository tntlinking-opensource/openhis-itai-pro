/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.upload.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 医保办待传/失败列表查询。
 */
@Getter
@Setter
public class QueryUploadPageRequest extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** CHS 上传状态过滤。 */
    private String statusCode;
}

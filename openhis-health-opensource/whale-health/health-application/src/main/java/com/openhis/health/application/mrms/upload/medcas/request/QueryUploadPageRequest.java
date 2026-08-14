/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 医保办待传/失败列表查询。
 */
@Getter
@Setter
public class QueryUploadPageRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** CHS 上传状态过滤。 */
    private String statusCode;
}

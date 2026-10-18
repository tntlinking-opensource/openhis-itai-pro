/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.request;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保办上报台分页查询（4401 / 4101A / 4102）。
 *
 * <p>交易号决定状态字段口径：4401 看 {@code chs_upload_*}；4101A/4102/空看结算上传标志。</p>
 */
@Getter
@Setter
public class QueryUploadPageRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /**
     * 上报状态过滤。
     *
     * <p>4401：{@code ChsUploadStatus} / {@code none}；4101A/4102：见领域 settle 映射（none/failed/success 等）。</p>
     */
    private String statusCode;

    /**
     * 交易号：{@code 4401} / {@code 4101A} / {@code 4102}；空表示按结算清单口径（非 4401）。
     */
    private String infno;

    /** 出院时间起（含）。 */
    private LocalDateTime dscgTimeFrom;

    /** 出院时间止（含）。 */
    private LocalDateTime dscgTimeTo;
}

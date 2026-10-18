/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询门诊收费队列请求。
 * <p>分页与通用检索字段见 {@link AbstractPageQuery}（代码生成通用规范 B.10.11）。操作科室由服务端从当前登录会话解析，不在请求体上传。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询门诊收费队列请求")
public class PageOutpatientChargeQueueRequest extends AbstractHealthPageRequest {

    /**
     * 队列页签编码（待收费/已收费等）。
     */
    /** 队列页签编码（业务字段）。 */
    @Schema(description = "队列页签编码")
    private String queueTabCode;
}

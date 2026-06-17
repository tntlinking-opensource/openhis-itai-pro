/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.financial.charge.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
/** 分页查询门诊收费队列请求。 */
@Schema(description = "分页查询门诊收费队列请求")
public class PageOutpatientChargeQueueRequest extends AbstractHealthPageRequest {

    /**
     * 队列页签编码（待收费/已收费等）。
     */
    @Schema(description = "队列页签编码")
    /** 队列页签编码（业务字段）。 */
    private String queueTabCode;
}

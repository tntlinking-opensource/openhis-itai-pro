/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊发药就诊队列分页请求。
 *
 * <p>分页与通用检索字段见 {@link AbstractHealthPageRequest}（代码生成通用规范 B.10.11）；待发药队列库房范围由服务端依据当前登录用户会话机构解析，调用方不传库房主键。</p>
 * <p>本接口不使用 {@code startDate}/{@code endDate}（发放事实不以 {@code status_changed_time} 时间窗筛选）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "门诊发药就诊队列分页请求")
public class PagePendingDispenseEncounterQueueRequest extends AbstractHealthPageRequest {

    /**
     * 发放状态 {@code DispenseStatus#getCode()} 集合（与持久化列、{@code EnumUtil} 一致，见 B.10.8.1）；空表示不按发放状态过滤。
     */
    @Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
    private List<String> dispenseStatusEnumIn;
}

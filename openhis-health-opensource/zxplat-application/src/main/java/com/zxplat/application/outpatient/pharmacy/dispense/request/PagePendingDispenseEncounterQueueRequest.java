/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊发药就诊队列分页请求。
 *
 * <p>分页与通用检索字段见 {@link AbstractHealthPageRequest}（代码生成通用规范 B.10.11）；待发药队列库房范围由服务端依据当前登录用户会话机构解析，调用方不传库房主键。</p>
 * <p>{@code startDate}/{@code endDate} 为可选时间窗，按就诊 {@code startTime} 收窄（与门诊收费队列同口径）；不使用发放行 {@code status_changed_time}。</p>
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

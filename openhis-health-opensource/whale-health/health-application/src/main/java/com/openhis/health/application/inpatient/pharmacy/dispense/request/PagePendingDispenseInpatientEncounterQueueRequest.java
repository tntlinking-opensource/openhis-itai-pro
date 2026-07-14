/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院发药在院患者队列分页请求（IF-IPP-02）。
 *
 * <p>本接口不使用 {@code startDate}/{@code endDate}（发放事实不以 {@code status_changed_time} 时间窗筛选）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院发药在院患者队列分页请求")
public class PagePendingDispenseInpatientEncounterQueueRequest extends AbstractHealthPageRequest {

    /** 病区库位主键；未传时不拼病区谓词（与护士站队列同族）。 */
    @Schema(description = "病区主键；未传或 null 表示不按病区过滤")
    private Long wardId;

    /** 发放状态 code 集合；空表示不按发放状态过滤。 */
    @Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
    private List<String> dispenseStatusEnumIn;
}

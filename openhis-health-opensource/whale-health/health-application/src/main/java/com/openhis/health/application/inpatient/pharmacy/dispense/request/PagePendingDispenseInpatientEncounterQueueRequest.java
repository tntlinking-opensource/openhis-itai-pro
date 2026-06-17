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

package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
/** 住院发药在院患者队列分页请求。 */
@Schema(description = "住院发药在院患者队列分页请求")
public class PagePendingDispenseInpatientEncounterQueueRequest extends AbstractHealthPageRequest {

    @Schema(description = "病区主键；未传或 null 表示不按病区过滤")
    private Long wardId;

    /** 发放状态 code 集合；空表示不按发放状态过滤。 */
    @Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
    private List<String> dispenseStatusEnumIn;
}

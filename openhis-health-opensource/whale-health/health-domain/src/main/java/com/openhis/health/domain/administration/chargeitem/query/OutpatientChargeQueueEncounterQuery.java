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

package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊收银队列：按收费事实收敛就诊主键分页查询入参。
 *
 * <p>分页键与关键字/就诊时段见 {@link AbstractPageQuery}；科室收窄由
 * {@link com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService} 从当前登录会话解析。</p>
 *
 * <p>通过 {@code adm_charge_item} 状态与 {@code hisrm_outpatient_info} 存在性关联限定「至少有一条符合收银/退费队列口径的收费明细」的就诊（收银见 {@code pageEncounterIdsForOutpatientCashierQueue}，退费见 {@code pageEncounterIdsForOutpatientRefundQueue}）。</p>
 */
@Getter
@Setter
public class OutpatientChargeQueueEncounterQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 排除的上游业务表锚点（必填时应由调用方设为 {@code TableNameConstants.ADM_HEALTHCARE_SERVICE}）。
     *
     * <p>与门诊收银待收明细 {@link ChargeItemQueryCommand#getExcludedServiceTable} 口径一致。</p>
     */
    private String excludedServiceTable;

    /**
     * 应用层预解析的门诊 AMB 就诊主键集合；由 {@code OutpatientChargeEncounterQueueLoader} 写入后领域层仅 {@code IN} 过滤。
     */
    private List<Long> resolvedEncounterScope;
}

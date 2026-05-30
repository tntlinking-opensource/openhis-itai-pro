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

package com.openhis.health.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按上游业务锚点作废未结算草稿收费明细（门诊医生站医嘱删行，AS-OP-DOR-003 IF-DOR-14）。
 */
@Getter
@Setter
public class VoidUnbilledDraftChargeItemsByServiceAnchorCommand {

    /** 操作人主键。 */
    private Long operatorId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 上游业务表名（与 adm_charge_item.service_table 一致）。 */
    private String serviceTable;

    /** 上游业务主键（与 adm_charge_item.service_id 一致）。 */
    private Long serviceId;
}

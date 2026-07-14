/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

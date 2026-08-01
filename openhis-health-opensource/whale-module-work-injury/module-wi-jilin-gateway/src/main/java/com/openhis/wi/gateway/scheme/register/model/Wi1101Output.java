/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1101】人员信息查询 output（10-P0 §3.2）。
 */
@Data
@Accessors(chain = true)
public class Wi1101Output {

    /** 人员编号 → yb_wi_encounter_anchor.psn_no。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 工伤认定 ID（取 diagnosis[] 首条 injureAffirmID）。 */
    private String injureAffirmId;
}

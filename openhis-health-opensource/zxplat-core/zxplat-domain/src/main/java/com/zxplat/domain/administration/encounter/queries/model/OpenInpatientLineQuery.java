/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 按门诊锚点查询「未取消住院线」的只读结果。
 *
 * <p>供住院申请防重（BR-IP-REQ-002）与医生站是否已申请展示。</p>
 */
@Getter
@Setter
public class OpenInpatientLineQuery {

    /** 是否存在未取消的住院就诊。 */
    private boolean exists;

    /** 住院就诊主键；无命中时为 null。 */
    private Long encounterId;

    /** 住院业务号；无命中时为 null。 */
    private String busNo;

    /** 住院就诊状态码。 */
    private String statusEnum;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.model;

/**
 * 工伤 invoke 业务阶段（yb_wi_invoke_session.phase）。
 */
public enum WiPhase {

    /** 9102 资格查询。 */
    REG_QUERY,
    /** 2301 审批上传 → 9102。 */
    REG_APPROVAL,
    /** 门诊收费 2201→2207。 */
    OP_SETTLE,
    /** 门诊退费 2208。 */
    OP_REVERSE,
    /** 总额对账 1320（与对账确认拆分，对齐老 HIS）。 */
    RECON,
    /** 明细对账 1321（按 ipt_otp_no 逐笔）。 */
    RECON_DETAIL,
    /** 对账确认 1322（须已结束月份）。 */
    RECON_CONFIRM;

    public String getCode() {
        return name();
    }

    /**
     * 从请求字符串解析 phase。
     *
     * @param code phase 码
     * @return 枚举；非法时 null
     */
    public static WiPhase fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

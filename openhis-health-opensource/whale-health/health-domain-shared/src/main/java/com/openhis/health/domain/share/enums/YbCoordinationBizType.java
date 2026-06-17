/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.enums;

/**
 * 医保协同单业务类型（对齐 03-概要设计 §4.2）。
 */
public enum YbCoordinationBizType {

    /** 门诊结算。 */
    OP_SETTLE,
    /** 门诊结算撤销/冲正。 */
    OP_SETTLE_REVERSE,
    /** 门诊费用明细上传。 */
    OP_FEE_UPLOAD,
    /** 住院登记/入院。 */
    IP_ADMIT,
    /** 住院费用明细上传。 */
    IP_FEE_UPLOAD,
    /** 住院预结算。 */
    IP_PRE_SETTLE,
    /** 住院结算。 */
    IP_SETTLE,
    /** 住院结算撤销/冲正。 */
    IP_SETTLE_REVERSE,
    /** 出院办理。 */
    IP_DISCHARGE,
    /** 发药/耗材追溯上传。 */
    DISPENSE_UPLOAD,
    /** 目录对照映射。 */
    CATALOG_MAPPING,
    /** 日对账。 */
    RECON_DAILY,
    /** 明细对账。 */
    RECON_DETAIL,
    /** 吉林清算对账。 */
    RECON_CLEAR_JL,
    /** 结算清单上传。 */
    SETTLEMENT_LIST,
    /** 平台补偿（异常恢复）。 */
    PLATFORM_COMP,
    /** 未知状态恢复。 */
    UNKNOWN_RECOVER;

    public String getCode() {
        return name();
    }

    public static YbCoordinationBizType fromCode(String code) {
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

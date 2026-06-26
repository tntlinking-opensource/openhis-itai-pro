package com.openhis.chs.domain;

/**
 * 医保模块业务错误码。
 */
public final class ChsErrorCodes {

    public static final String CONTRACT_INVALID = "INS-C01";
    public static final String CONTRACT_NOT_FOUND = "INS-C02";
    public static final String PLATFORM_FAILED = "INS-C03";
    public static final String FILE_PARSE_FAILED = "INS-C04";
    public static final String MAPPING_FAILED = "INS-C05";
    public static final String SIGN_IN_FAILED = "INS-C06";
    /** 同一 payment 对应多笔医保结算，无法唯一定位。 */
    public static final String SETTLEMENT_AMBIGUOUS = "INS-C07";
    /** 结算领域不变量违反（状态机/金额恒等式/2207 透传）。 */
    public static final String SETTLEMENT_INVARIANT = "INS-C08";

    private ChsErrorCodes() {
    }
}

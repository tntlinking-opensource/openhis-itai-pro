package com.openhis.whale.einvoice.boss.contracts.enums;

/**
 * 医疗通用票业务标识（博思 busType）。
 */
public enum CommonBusType {

    /** 往来票。 */
    INTERCOURSE("09"),

    /** 捐赠票。 */
    DONATION("10"),

    /** 非税通用票。 */
    NON_TAX("11");

    private final String code;

    CommonBusType(String code) {
        this.code = code;
    }

    /**
     * 博思 busType 值。
     *
     * @return busType
     */
    public String getCode() {
        return code;
    }
}

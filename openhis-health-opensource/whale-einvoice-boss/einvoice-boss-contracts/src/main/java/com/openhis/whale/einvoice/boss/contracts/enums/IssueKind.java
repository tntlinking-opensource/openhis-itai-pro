package com.openhis.whale.einvoice.boss.contracts.enums;

/**
 * 博思开票业务种类（对应报文 busType / issue_kind）。
 */
public enum IssueKind {

    /** 挂号电子票据（invEBillRegistration）。 */
    REG("REG", "06"),

    /** 门诊电子票据（invoiceEBillOutpatient）。 */
    AMB("AMB", "02"),

    /** 住院电子票据（invEBillHospitalized，P1）。 */
    IMP("IMP", "01"),

    /** 医疗通用电子票据（invoiceEBillCommon，P3 · 往来/捐赠/非税）。 */
    COM("COM", null);

    private final String code;
    private final String busType;

    IssueKind(String code, String busType) {
        this.code = code;
        this.busType = busType;
    }

    /**
     * 落库与 API 使用的业务码。
     *
     * @return issue_kind 字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 博思报文 busType 字段值。
     *
     * @return busType
     */
    public String getBusType() {
        return busType;
    }
}

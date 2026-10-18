/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.share;

/**
 * 博思平台 serviceId 常量。
 */
public final class BossServiceIds {

    /** 挂号电子票据开具。 */
    public static final String INV_E_BILL_REGISTRATION = "invEBillRegistration";

    /** 门诊电子票据开具。 */
    public static final String INVOICE_E_BILL_OUTPATIENT = "invoiceEBillOutpatient";

    /** 住院电子票据开具。 */
    public static final String INV_E_BILL_HOSPITALIZED = "invEBillHospitalized";

    /** 电子票据冲红。 */
    public static final String WRITE_OFF_E_BILL = "writeOffEBill";

    /** 按业务流水号查票。 */
    public static final String GET_E_BILL_BY_BUS_NO = "getEBillByBusNo";

    /** 连通性测试。 */
    public static final String TEST_SERVER_CONNECT = "testServerConnect";

    /** 总笔数核对（按业务日期）。 */
    public static final String CHECK_TOTAL_DATA = "checkTotalData";

    /** 总笔数核对（按开票日期）。 */
    public static final String CHECK_TOTAL_DATA_BY_IVC_DATE = "checkTotalDataByIvcDate";

    /** 医疗通用电子票据开具（P3）。 */
    public static final String INVOICE_E_BILL_COMMON = "invoiceEBillCommon";

    /** 查询电子票据入账状态（P3）。 */
    public static final String GET_E_BILL_ACCOUNT_STATUS = "getEBillAccountStatus";

    private BossServiceIds() {
    }
}

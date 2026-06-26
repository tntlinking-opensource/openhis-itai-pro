package com.openhis.chs.api;

/**
 * 常用交易编号。
 */
public final class ChsInfNo {

    public static final String SIGN_IN = "9001";
    public static final String SIGN_OUT = "9002";
    public static final String PERSON_INFO = "1101";
    /** 2001 人员待遇享受检查。 */
    public static final String PERSON_TREATMENT_CHECK = "2001";
    public static final String OUTPATIENT_REG = "2201";
    /** 2202 门诊挂号撤销。 */
    public static final String OUTPATIENT_REG_REVOKE = "2202";
    public static final String OUTPATIENT_VISIT_UPLOAD = "2203";
    public static final String OUTPATIENT_FEE_UPLOAD = "2204";
    public static final String OUTPATIENT_PRE_SETTLE = "2206";
    public static final String OUTPATIENT_SETTLE = "2207";
    public static final String OUTPATIENT_FEE_REVOKE = "2205";
    public static final String OUTPATIENT_SETTLE_REVOKE = "2208";
    public static final String INPATIENT_FEE_UPLOAD = "2301";
    public static final String INPATIENT_FEE_REVOKE = "2302";
    public static final String INPATIENT_PRE_SETTLE = "2303";
    public static final String INPATIENT_SETTLE = "2304";
    public static final String INPATIENT_SETTLE_REVOKE = "2305";
    public static final String INPATIENT_ADMISSION = "2401";
    public static final String INPATIENT_DISCHARGE = "2402";
    public static final String INPATIENT_INFO_CHANGE = "2403";
    public static final String INPATIENT_ADMISSION_REVOKE = "2404";
    public static final String INPATIENT_DISCHARGE_REVOKE = "2405";
    public static final String CATALOG_QUERY = "1312";
    public static final String CATALOG_MAPPING_UPLOAD = "3301";
    public static final String CATALOG_MAPPING_REVOKE = "3302";
    public static final String RECON_DAILY_LEDGER = "3201";
    public static final String RECON_DETAIL_LEDGER = "3202";
    public static final String FILE_UPLOAD = "9101";
    public static final String CLEAR_APPLY_JILIN = "3203A";
    public static final String CLEAR_APPLY_REVOKE_JILIN = "3204A";
    public static final String CLEAR_STATUS_QUERY_JILIN = "3205A";
    public static final String CLEAR_ORG_QUERY_JILIN = "3206A";
    public static final String CLEAR_CROSS_PROVINCE_RECON_JILIN = "3209A";
    public static final String DRUG_CATALOG_DOWNLOAD = "1301";
    public static final String HERB_CATALOG_DOWNLOAD = "1302";
    public static final String MEDICAL_SERVICE_CATALOG_DOWNLOAD = "1305";
    public static final String MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD = "1306";
    public static final String DISEASE_CATALOG_DOWNLOAD = "1307";
    public static final String SURGERY_CATALOG_DOWNLOAD = "1308";
    public static final String SPECIAL_DISEASE_CATALOG_DOWNLOAD = "1309";
    public static final String TCM_DISEASE_CATALOG_DOWNLOAD = "1314";
    public static final String TCM_SYNDROME_CATALOG_DOWNLOAD = "1315";
    public static final String FILE_DOWNLOAD = "9102";
    public static final String INVENTORY_STOCKTAKE = "3501";
    public static final String INVENTORY_CHANGE = "3502";
    public static final String INVENTORY_PURCHASE = "3503";
    public static final String INVENTORY_PURCHASE_RETURN = "3504";
    public static final String INVENTORY_SALE = "3505";
    public static final String INVENTORY_SALE_RETURN = "3506";
    public static final String INVENTORY_DELETE = "3507";
    public static final String INVENTORY_STOCK_QUERY = "3508";
    public static final String INVENTORY_CHANGE_QUERY = "3509";
    public static final String INVENTORY_PURCHASE_QUERY = "3510";
    public static final String INVENTORY_SALE_QUERY = "3511";
    public static final String INVENTORY_INBOUND_TRACE_QUERY = "3512";
    public static final String INVENTORY_SALE_TRACE_QUERY = "3513";
    public static final String MDTRT_QUERY = "5201";
    public static final String DISE_QUERY = "5202";
    public static final String SETL_QUERY = "5203";
    public static final String FEE_DETAIL_QUERY = "5204";
    public static final String OPSP_DRUG_QUERY = "5205";
    public static final String PERSON_CUM_QUERY = "5206";
    /** 5301 人员慢特病备案查询。 */
    public static final String PERSON_OPSP_FILING_QUERY = "5301";
    /** 2601 冲正交易。 */
    public static final String TRANSACTION_CORRECT = "2601";
    /** 1901 字典表查询。 */
    public static final String DICT_QUERY = "1901";

    private ChsInfNo() {
    }
}

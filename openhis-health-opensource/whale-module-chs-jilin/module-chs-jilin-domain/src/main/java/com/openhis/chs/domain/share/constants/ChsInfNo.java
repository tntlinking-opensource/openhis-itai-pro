/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.constants;

import java.util.Map;

/**
 * 国家医保平台 HTTP 请求头/报文中的 {@code infno}（交易编号）。
 *
 * <p>每次调用网关时传 {@code infno=2207} 这类四位（或带 A 后缀）字符串。
 * 本类是<b>交易号唯一来源</b>；日志展示可用 {@link #resolveDisplayName(String)}。</p>
 *
 * <p>字段字典（med_type、inv_chg_type 等）不在此维护，见 {@code share.enums}。</p>
 */
public final class ChsInfNo {

    // —— 会话 ——
    /** 【9001】签到 */
    public static final String SIGN_IN = "9001";
    /** 【9002】签退 */
    public static final String SIGN_OUT = "9002";

    // —— 人员 / 待遇 ——
    /** 【1101】人员信息获取（读卡） */
    public static final String PERSON_INFO = "1101";
    /** 【2001】人员待遇享受检查 */
    public static final String PERSON_TREATMENT_CHECK = "2001";

    // —— 门诊结算链 22xx ——
    /** 【2201】门诊挂号 */
    public static final String OUTPATIENT_REG = "2201";
    /** 【2202】门诊挂号撤销 */
    public static final String OUTPATIENT_REG_REVOKE = "2202";
    /** 【2203】门诊就诊信息上传 */
    public static final String OUTPATIENT_VISIT_UPLOAD = "2203";
    /** 【2204】门诊费用明细上传 */
    public static final String OUTPATIENT_FEE_UPLOAD = "2204";
    /** 【2205】门诊费用明细撤销 */
    public static final String OUTPATIENT_FEE_REVOKE = "2205";
    /** 【2206】门诊预结算 */
    public static final String OUTPATIENT_PRE_SETTLE = "2206";
    /** 【2207】门诊正式结算 */
    public static final String OUTPATIENT_SETTLE = "2207";
    /** 【2208】门诊结算撤销 */
    public static final String OUTPATIENT_SETTLE_REVOKE = "2208";

    // —— 住院结算链 23xx / 24xx ——
    /** 【2301】住院费用明细上传 */
    public static final String INPATIENT_FEE_UPLOAD = "2301";
    /** 【2302】住院费用明细撤销 */
    public static final String INPATIENT_FEE_REVOKE = "2302";
    /** 【2303】住院预结算 */
    public static final String INPATIENT_PRE_SETTLE = "2303";
    /** 【2304】住院正式结算 */
    public static final String INPATIENT_SETTLE = "2304";
    /** 【2305】住院结算撤销 */
    public static final String INPATIENT_SETTLE_REVOKE = "2305";
    /** 【2401】入院办理 */
    public static final String INPATIENT_ADMISSION = "2401";
    /** 【2402】出院办理 */
    public static final String INPATIENT_DISCHARGE = "2402";
    /** 【2403】住院信息变更 */
    public static final String INPATIENT_INFO_CHANGE = "2403";
    /** 【2404】入院撤销 */
    public static final String INPATIENT_ADMISSION_REVOKE = "2404";
    /** 【2405】出院撤销 */
    public static final String INPATIENT_DISCHARGE_REVOKE = "2405";

    // —— 目录 / 对码 ——
    /** 【1312】医保目录信息查询（增量） */
    public static final String CATALOG_QUERY = "1312";
    /** 【3301】目录对照上传 */
    public static final String CATALOG_MAPPING_UPLOAD = "3301";
    /** 【3302】目录对照撤销 */
    public static final String CATALOG_MAPPING_REVOKE = "3302";

    // —— 对账 / 清算 / 文件 ——
    /** 【3201】医药机构费用结算对总账 */
    public static final String RECON_DAILY_LEDGER = "3201";
    /** 【3202】医药机构费用结算对明细账 */
    public static final String RECON_DETAIL_LEDGER = "3202";
    /** 【9101】文件上传（目录 ZIP 等） */
    public static final String FILE_UPLOAD = "9101";
    /** 【9102】文件下载 */
    public static final String FILE_DOWNLOAD = "9102";
    /** 【3203A】清算申请（吉林） */
    public static final String CLEAR_APPLY_JILIN = "3203A";
    /** 【3204A】清算申请撤销（吉林） */
    public static final String CLEAR_APPLY_REVOKE_JILIN = "3204A";
    /** 【3205A】清算状态查询（吉林） */
    public static final String CLEAR_STATUS_QUERY_JILIN = "3205A";
    /** 【3206A】清算机构查询（吉林） */
    public static final String CLEAR_ORG_QUERY_JILIN = "3206A";
    /** 【3209A】跨省异地对账（吉林） */
    public static final String CLEAR_CROSS_PROVINCE_RECON_JILIN = "3209A";

    // —— 目录文件下载 13xx ——
    /** 【1301】西药中成药目录下载 */
    public static final String DRUG_CATALOG_DOWNLOAD = "1301";
    /** 【1302】中药饮片目录下载 */
    public static final String HERB_CATALOG_DOWNLOAD = "1302";
    /** 【1305】医疗服务项目目录下载 */
    public static final String MEDICAL_SERVICE_CATALOG_DOWNLOAD = "1305";
    /** 【1306】医用耗材目录下载 */
    public static final String MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD = "1306";
    /** 【1307】疾病与诊断目录下载 */
    public static final String DISEASE_CATALOG_DOWNLOAD = "1307";
    /** 【1308】手术操作目录下载 */
    public static final String SURGERY_CATALOG_DOWNLOAD = "1308";
    /** 【1309】门诊慢特病种目录下载 */
    public static final String SPECIAL_DISEASE_CATALOG_DOWNLOAD = "1309";
    /** 【1314】中医疾病目录下载 */
    public static final String TCM_DISEASE_CATALOG_DOWNLOAD = "1314";
    /** 【1315】中医证候目录下载 */
    public static final String TCM_SYNDROME_CATALOG_DOWNLOAD = "1315";

    // —— 进销存 350x ——
    /** 【3501】商品盘存上传（机构初始化） */
    public static final String INVENTORY_STOCKTAKE = "3501";
    /** 【3502】商品库存变更（盈亏/调拨/领用等） */
    public static final String INVENTORY_CHANGE = "3502";
    /** 【3503】商品采购（入库） */
    public static final String INVENTORY_PURCHASE = "3503";
    /** 【3504】商品采购退货 */
    public static final String INVENTORY_PURCHASE_RETURN = "3504";
    /** 【3505】商品销售（发药） */
    public static final String INVENTORY_SALE = "3505";
    /** 【3506】商品销售退货 */
    public static final String INVENTORY_SALE_RETURN = "3506";
    /** 【3507】商品信息删除 */
    public static final String INVENTORY_DELETE = "3507";
    /** 【3508】商品库存信息查询 */
    public static final String INVENTORY_STOCK_QUERY = "3508";
    /** 【3509】商品库存变更记录查询 */
    public static final String INVENTORY_CHANGE_QUERY = "3509";
    /** 【3510】商品采购信息查询 */
    public static final String INVENTORY_PURCHASE_QUERY = "3510";
    /** 【3511】商品销售信息查询 */
    public static final String INVENTORY_SALE_QUERY = "3511";
    /** 【3512】入库商品追溯信息查询 */
    public static final String INVENTORY_INBOUND_TRACE_QUERY = "3512";
    /** 【3513】销售商品追溯信息查询 */
    public static final String INVENTORY_SALE_TRACE_QUERY = "3513";

    // —— 信息查询 52xx / 其它 ——
    /** 【5201】就诊信息查询 */
    public static final String MDTRT_QUERY = "5201";
    /** 【5202】诊断信息查询 */
    public static final String DISE_QUERY = "5202";
    /** 【5203】结算信息查询 */
    public static final String SETL_QUERY = "5203";
    /** 【5204】费用明细查询 */
    public static final String FEE_DETAIL_QUERY = "5204";
    /** 【5205】人员慢特病用药记录查询 */
    public static final String OPSP_DRUG_QUERY = "5205";
    /** 【5206】人员累计信息查询 */
    public static final String PERSON_CUM_QUERY = "5206";
    /** 【5301】人员慢特病备案查询 */
    public static final String PERSON_OPSP_FILING_QUERY = "5301";
    /** 【2601】冲正交易 */
    public static final String TRANSACTION_CORRECT = "2601";
    /** 【1901】字典表查询/下载 */
    public static final String DICT_QUERY = "1901";
    /** 【4401】住院病案首页信息上传 */
    public static final String MEDCAS_HOMEPAGE = "4401";

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry(SIGN_IN, "签到"),
        Map.entry(SIGN_OUT, "签退"),
        Map.entry(PERSON_INFO, "人员信息获取"),
        Map.entry(PERSON_TREATMENT_CHECK, "人员待遇享受检查"),
        Map.entry(OUTPATIENT_REG, "门诊挂号"),
        Map.entry(OUTPATIENT_REG_REVOKE, "门诊挂号撤销"),
        Map.entry(OUTPATIENT_VISIT_UPLOAD, "门诊就诊信息上传"),
        Map.entry(OUTPATIENT_FEE_UPLOAD, "门诊费用明细上传"),
        Map.entry(OUTPATIENT_FEE_REVOKE, "门诊费用明细撤销"),
        Map.entry(OUTPATIENT_PRE_SETTLE, "门诊预结算"),
        Map.entry(OUTPATIENT_SETTLE, "门诊结算"),
        Map.entry(OUTPATIENT_SETTLE_REVOKE, "门诊结算撤销"),
        Map.entry(INPATIENT_FEE_UPLOAD, "住院费用明细上传"),
        Map.entry(INPATIENT_FEE_REVOKE, "住院费用明细撤销"),
        Map.entry(INPATIENT_PRE_SETTLE, "住院预结算"),
        Map.entry(INPATIENT_SETTLE, "住院结算"),
        Map.entry(INPATIENT_SETTLE_REVOKE, "住院结算撤销"),
        Map.entry(INPATIENT_ADMISSION, "入院办理"),
        Map.entry(INPATIENT_DISCHARGE, "出院办理"),
        Map.entry(INPATIENT_INFO_CHANGE, "住院信息变更"),
        Map.entry(INPATIENT_ADMISSION_REVOKE, "入院撤销"),
        Map.entry(INPATIENT_DISCHARGE_REVOKE, "出院撤销"),
        Map.entry(CATALOG_QUERY, "医保目录信息查询"),
        Map.entry(CATALOG_MAPPING_UPLOAD, "目录对照上传"),
        Map.entry(CATALOG_MAPPING_REVOKE, "目录对照撤销"),
        Map.entry(RECON_DAILY_LEDGER, "医药机构费用结算对总账"),
        Map.entry(RECON_DETAIL_LEDGER, "医药机构费用结算对明细账"),
        Map.entry(FILE_UPLOAD, "文件上传"),
        Map.entry(FILE_DOWNLOAD, "文件下载"),
        Map.entry(CLEAR_APPLY_JILIN, "清算申请（吉林）"),
        Map.entry(CLEAR_APPLY_REVOKE_JILIN, "清算申请撤销（吉林）"),
        Map.entry(CLEAR_STATUS_QUERY_JILIN, "清算状态查询（吉林）"),
        Map.entry(CLEAR_ORG_QUERY_JILIN, "清算机构查询（吉林）"),
        Map.entry(CLEAR_CROSS_PROVINCE_RECON_JILIN, "跨省异地对账（吉林）"),
        Map.entry(DRUG_CATALOG_DOWNLOAD, "西药中成药目录下载"),
        Map.entry(HERB_CATALOG_DOWNLOAD, "中药饮片目录下载"),
        Map.entry(MEDICAL_SERVICE_CATALOG_DOWNLOAD, "医疗服务项目目录下载"),
        Map.entry(MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD, "医用耗材目录下载"),
        Map.entry(DISEASE_CATALOG_DOWNLOAD, "疾病与诊断目录下载"),
        Map.entry(SURGERY_CATALOG_DOWNLOAD, "手术操作目录下载"),
        Map.entry(SPECIAL_DISEASE_CATALOG_DOWNLOAD, "门诊慢特病种目录下载"),
        Map.entry(TCM_DISEASE_CATALOG_DOWNLOAD, "中医疾病目录下载"),
        Map.entry(TCM_SYNDROME_CATALOG_DOWNLOAD, "中医证候目录下载"),
        Map.entry(INVENTORY_STOCKTAKE, "商品盘存上传"),
        Map.entry(INVENTORY_CHANGE, "商品库存变更"),
        Map.entry(INVENTORY_PURCHASE, "商品采购"),
        Map.entry(INVENTORY_PURCHASE_RETURN, "商品采购退货"),
        Map.entry(INVENTORY_SALE, "商品销售"),
        Map.entry(INVENTORY_SALE_RETURN, "商品销售退货"),
        Map.entry(INVENTORY_DELETE, "商品信息删除"),
        Map.entry(INVENTORY_STOCK_QUERY, "商品库存信息查询"),
        Map.entry(INVENTORY_CHANGE_QUERY, "商品库存变更记录查询"),
        Map.entry(INVENTORY_PURCHASE_QUERY, "商品采购信息查询"),
        Map.entry(INVENTORY_SALE_QUERY, "商品销售信息查询"),
        Map.entry(INVENTORY_INBOUND_TRACE_QUERY, "入库商品追溯信息查询"),
        Map.entry(INVENTORY_SALE_TRACE_QUERY, "销售商品追溯信息查询"),
        Map.entry(MDTRT_QUERY, "就诊信息查询"),
        Map.entry(DISE_QUERY, "诊断信息查询"),
        Map.entry(SETL_QUERY, "结算信息查询"),
        Map.entry(FEE_DETAIL_QUERY, "费用明细查询"),
        Map.entry(OPSP_DRUG_QUERY, "人员慢特病用药记录查询"),
        Map.entry(PERSON_CUM_QUERY, "人员累计信息查询"),
        Map.entry(PERSON_OPSP_FILING_QUERY, "人员慢特病备案查询"),
        Map.entry(TRANSACTION_CORRECT, "冲正交易"),
        Map.entry(DICT_QUERY, "字典表查询"),
        Map.entry(MEDCAS_HOMEPAGE, "住院病案首页信息上传"));

    /**
     * infno → 中文交易名（运维日志、界面展示）；未知编号原样返回。
     *
     * @param infno 交易编号，如 {@code 3502}
     * @return 中文名称；空入参返回 {@code null}
     */
    public static String resolveDisplayName(String infno) {
        if (infno == null || infno.isBlank()) {
            return null;
        }
        String key = infno.trim();
        return DISPLAY_NAMES.getOrDefault(key, key);
    }

    private ChsInfNo() {
    }
}

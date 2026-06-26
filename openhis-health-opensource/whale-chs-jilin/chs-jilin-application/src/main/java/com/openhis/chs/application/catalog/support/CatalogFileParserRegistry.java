/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 13xx 文件型目录解析规格注册表（列索引对齐 OpenHIS.YiBao Models）。
 */
public final class CatalogFileParserRegistry {

    private static final Map<String, CatalogFileParserSpec> SPECS = new LinkedHashMap<>();

    static {
        register(spec1301());
        register(spec1302());
        register(spec1305());
        register(spec1306());
        register(spec1307());
        register(spec1308());
        register(spec1309());
        register(spec1314());
        register(spec1315());
    }

    private CatalogFileParserRegistry() {
    }

    public static Set<String> supportedInfno() {
        return SPECS.keySet();
    }

    public static CatalogFileParserSpec require(String infno) {
        if (infno == null) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "目录 infno 不能为空");
        }
        CatalogFileParserSpec spec = SPECS.get(infno.trim());
        if (spec == null) {
            throw new BusinessException(
                ChsErrorCodes.FILE_PARSE_FAILED,
                "暂不支持目录下载 infno=" + infno + "，已支持: " + String.join(", ", SPECS.keySet()));
        }
        return spec;
    }

    private static void register(CatalogFileParserSpec spec) {
        SPECS.put(spec.infno(), spec);
    }

    /** 1301 西药中成药 */
    private static CatalogFileParserSpec spec1301() {
        return new CatalogFileParserSpec(
            ChsInfNo.DRUG_CATALOG_DOWNLOAD,
            ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG,
            ChsInfNo.DRUG_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 3, 9, 13, 50, 61, 53, 78, 91, 36, 37, 81, 82, 83);
    }

    /** 1302 中药饮片 */
    private static CatalogFileParserSpec spec1302() {
        return new CatalogFileParserSpec(
            ChsInfNo.HERB_CATALOG_DOWNLOAD,
            ChsCatalogConstants.LIST_TYPE_HERB,
            ChsInfNo.HERB_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 1, -1, -1, -1, -1, -1, 13, -1, 11, 12, 16, 17, 18);
    }

    /** 1305 医疗服务项目 */
    private static CatalogFileParserSpec spec1305() {
        return new CatalogFileParserSpec(
            ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD,
            ChsCatalogConstants.LIST_TYPE_MEDICAL_SERVICE,
            ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 9, -1, -1, -1, -1, -1, 6, -1, 11, 12, -1, 14, 15);
    }

    /** 1306 医用耗材 */
    private static CatalogFileParserSpec spec1306() {
        return new CatalogFileParserSpec(
            ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD,
            ChsCatalogConstants.LIST_TYPE_MEDICAL_CONSUMABLE,
            ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 4, -1, 7, -1, 50, -1, 67, -1, 46, 47, -1, 69, 70);
    }

    /**
     * 1307 疾病与诊断（规范 24 列 TAB，见 docs/apis/13xx/1307）。
     * <p>主键：col11 诊断代码、col12 诊断名称（index 10/11）；版本链对齐 OpenHIS {@code version_number} index 22（J001_*）。</p>
     */
    private static CatalogFileParserSpec spec1307() {
        return new CatalogFileParserSpec(
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            10, 11, -1, -1, -1, -1, -1, 18, -1, -1, -1, 21, 22, 23);
    }

    /** 1308 手术操作：col11/12=操作代码/名称（index 10/11）；版本链 index 22 对齐 OpenHIS */
    private static CatalogFileParserSpec spec1308() {
        return new CatalogFileParserSpec(
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            10, 11, -1, -1, -1, -1, -1, 18, -1, -1, -1, 21, 22, 23);
    }

    /** 1309 门诊慢特病：col1/15=代码/名称（index 0/14）；版本链 col10=index 9 */
    private static CatalogFileParserSpec spec1309() {
        return new CatalogFileParserSpec(
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            0, 14, -1, -1, -1, -1, -1, 5, -1, -1, -1, 8, 9, 11);
    }

    /** 1314 中医疾病 */
    private static CatalogFileParserSpec spec1314() {
        return new CatalogFileParserSpec(
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            5, 6, -1, -1, -1, -1, -1, 8, -1, -1, -1, 11, 12, 13);
    }

    /** 1315 中医证候：col2/3=类目代码/名称（index 1/2）；版本链 col13=index 12 对齐 OpenHIS {@code version} */
    private static CatalogFileParserSpec spec1315() {
        return new CatalogFileParserSpec(
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            1, 2, -1, -1, -1, -1, -1, 8, -1, -1, -1, 11, 12, 13);
    }
}

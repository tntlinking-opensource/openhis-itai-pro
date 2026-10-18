/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import com.zxplat.plugin.chs.domain.share.enums.ListType;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 13xx 文件型目录解析规格注册表。
 * <p>按 infno 注册 {@link CatalogFileParserSpec}，列索引对齐平台.YiBao Models，
 * 供 {@link CatalogFileParser} 与阶段一版本链扫描使用。</p>
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
        register(spec1310());
        register(spec1311());
        register(spec1313());
        register(spec1314());
        register(spec1315());
    }

    private CatalogFileParserRegistry() {
    }

    /** 返回已注册的 infno 集合（保持注册顺序）。 */
    public static Set<String> supportedInfno() {
        return SPECS.keySet();
    }

    /**
     * 按 infno 获取解析规格，不支持时抛出业务异常。
     *
     * @param infno 医保接口编号
     * @return 对应列映射规格
     */
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
            ListType.WESTERN_DRUG.getCode(),
            ChsInfNo.DRUG_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 3, 9, 13, 50, 61, 53, 78, 91, 36, 37, 81, 82, 83);
    }

    /** 1302 中药饮片 */
    private static CatalogFileParserSpec spec1302() {
        return new CatalogFileParserSpec(
            ChsInfNo.HERB_CATALOG_DOWNLOAD,
            ListType.HERB.getCode(),
            ChsInfNo.HERB_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 1, -1, -1, -1, -1, -1, 13, -1, 11, 12, 16, 17, 18);
    }

    /** 1305 医疗服务项目 */
    private static CatalogFileParserSpec spec1305() {
        return new CatalogFileParserSpec(
            ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD,
            ListType.MEDICAL_SERVICE.getCode(),
            ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 9, -1, -1, -1, -1, -1, 6, -1, 11, 12, -1, 14, 15);
    }

    /** 1306 医用耗材 */
    private static CatalogFileParserSpec spec1306() {
        return new CatalogFileParserSpec(
            ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD,
            ListType.MEDICAL_CONSUMABLE.getCode(),
            ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NAME_THEN_NUMBER,
            0, 4, -1, 7, -1, 50, -1, 67, -1, 46, 47, -1, 69, 70);
    }

    /**
     * 1307 疾病与诊断（规范 24 列）。
     * <p>code/name=第11/12列诊断代码/名称（idx 10/11）；vali=第19列（idx 18）；
     * verNo/verName=第23/24列（idx 22/23）。章/节/类目/亚目行诊断码常为空或字面量 null，解析时跳过。</p>
     */
    private static CatalogFileParserSpec spec1307() {
        return new CatalogFileParserSpec(
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            10, 11, -1, -1, -1, -1, -1, 18, -1, -1, -1, 21, 22, 23);
    }

    /**
     * 1308 手术操作（规范 24 列）。
     * <p>code/name=第11/12列手术操作代码/名称（idx 10/11）；vali=第19列（idx 18）；
     * verNo/verName=第23/24列（idx 22/23）。</p>
     */
    private static CatalogFileParserSpec spec1308() {
        return new CatalogFileParserSpec(
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            10, 11, -1, -1, -1, -1, -1, 18, -1, -1, -1, 21, 22, 23);
    }

    /**
     * 1309 门诊慢特病（与 1310/1311 列位不同）。
     * <p>code=第1列目录代码（idx 0）；name=第15列病种名称（idx 14）；
     * vali=第6列（idx 5）；verNo=第10列（idx 9）；verName=第12列（idx 11）。</p>
     */
    private static CatalogFileParserSpec spec1309() {
        return new CatalogFileParserSpec(
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            0, 14, -1, -1, -1, -1, -1, 5, -1, -1, -1, 8, 9, 11);
    }

    /**
     * 1310 按病种付费。
     * <p>code/name=第2/3列病种目录代码/名称（idx 1/2）；vali=第6列（idx 5）；
     * updt=第9列（idx 8）；verNo=第10列（idx 9）；verName=第13列（idx 12）。
     * 注意：与 1311 不同，有效标志不在第4列。</p>
     */
    private static CatalogFileParserSpec spec1310() {
        return new CatalogFileParserSpec(
            ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD,
            ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD,
            ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            1, 2, -1, -1, -1, -1, -1, 5, -1, -1, -1, 8, 9, 12);
    }

    /**
     * 1311 日间手术治疗病种（列布局独立，勿与 1309/1310 混用）。
     * <p>code/name=第2/3列病种目录代码/名称（idx 1/2）；vali=第4列（idx 3）；
     * updt=第7列（idx 6）；verNo=第8列（idx 7）；verName=第11列（idx 10）；
     * 第14/15列=手术操作名称/代码（本解析不映射到 hilist）。</p>
     */
    private static CatalogFileParserSpec spec1311() {
        return new CatalogFileParserSpec(
            ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            1, 2, -1, -1, -1, -1, -1, 3, -1, -1, -1, 6, 7, 10);
    }

    /**
     * 1313 肿瘤形态学。
     * <p>code/name=第4/5列形态学分类代码/名称（idx 3/4）；vali=第6列（idx 5）；
     * verNo/verName=第10/11列（idx 9/10）。第2/3列为肿瘤/细胞类型，不作主键。</p>
     */
    private static CatalogFileParserSpec spec1313() {
        return new CatalogFileParserSpec(
            ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD,
            ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD,
            ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            3, 4, -1, -1, -1, -1, -1, 5, -1, -1, -1, 8, 9, 10);
    }

    /**
     * 1314 中医疾病。
     * <p>code/name=第6/7列疾病分类代码/名称（idx 5/6）；vali=第9列（idx 8）；
     * verNo/verName=第13/14列（idx 12/13）。第2–5列科别/专科类目常为字面量 null。</p>
     */
    private static CatalogFileParserSpec spec1314() {
        return new CatalogFileParserSpec(
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            5, 6, -1, -1, -1, -1, -1, 8, -1, -1, -1, 11, 12, 13);
    }

    /**
     * 1315 中医证候（与 1314 同列布局，语义不同）。
     * <p>code/name=第6/7列证候分类代码/名称（idx 5/6）；vali=第9列（idx 8）；
     * verNo/verName=第13/14列（idx 12/13）。第2–5列类目/属性常为字面量 null，不可作主键。</p>
     */
    private static CatalogFileParserSpec spec1315() {
        return new CatalogFileParserSpec(
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD,
            CatalogVersionChainMode.VERSION_NUMBER_ONLY,
            5, 6, -1, -1, -1, -1, -1, 8, -1, -1, -1, 11, 12, 13);
    }
}

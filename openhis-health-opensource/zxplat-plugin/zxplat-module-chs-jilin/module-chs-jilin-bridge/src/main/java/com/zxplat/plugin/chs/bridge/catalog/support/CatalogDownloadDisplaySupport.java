/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import com.zxplat.plugin.chs.domain.share.enums.ListType;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 目录下载游标/版本号 UI 展示辅助。
 * <p>将 infno、版本号、文件名、同步时间格式化为前端可读文案，不含业务判断。</p>
 */
public final class CatalogDownloadDisplaySupport {

    private static final DateTimeFormatter SUCCESS_AT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CatalogDownloadDisplaySupport() {
    }

    /**
     * 将 infno 转为带中文说明的标签（如「1301 西药中成药」）。
     *
     * @param infno 医保接口编号，空则返回「-」
     * @return 展示用标签
     */
    public static String infnoLabel(String infno) {
        if (isEmpty(infno)) {
            return "-";
        }
        return switch (infno.trim()) {
            case ChsInfNo.DRUG_CATALOG_DOWNLOAD -> "1301 西药中成药";
            case ChsInfNo.HERB_CATALOG_DOWNLOAD -> "1302 中药饮片";
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> "1305 医疗服务项目";
            case ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD -> "1306 医用耗材";
            case ChsInfNo.DISEASE_CATALOG_DOWNLOAD -> "1307 疾病与诊断";
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> "1308 手术操作";
            case ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD -> "1309 门诊慢特病";
            case ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD -> "1310 按病种付费";
            case ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD -> "1311 日间手术病种";
            case ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD -> "1313 肿瘤形态学";
            case ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD -> "1314 中医疾病";
            case ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD -> "1315 中医证候";
            default -> infno.trim();
        };
    }

    /**
     * 将 infno 映射为目录类别 {@code list_type}（写入国家目录镜像时使用）。
     *
     * @param infno 医保接口编号
     * @return 目录类别编码，未知 infno 默认西药中成药
     */
    public static String listTypeForInfno(String infno) {
        if (isEmpty(infno)) {
            return ListType.WESTERN_DRUG.getCode();
        }
        return switch (infno.trim()) {
            case ChsInfNo.DRUG_CATALOG_DOWNLOAD -> ListType.WESTERN_DRUG.getCode();
            case ChsInfNo.HERB_CATALOG_DOWNLOAD -> ListType.HERB.getCode();
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> ListType.MEDICAL_SERVICE.getCode();
            case ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD -> ListType.MEDICAL_CONSUMABLE.getCode();
            case ChsInfNo.DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> ChsInfNo.SURGERY_CATALOG_DOWNLOAD;
            case ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD -> ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD;
            case ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD -> ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD -> ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD;
            default -> ListType.WESTERN_DRUG.getCode();
        };
    }

    /**
     * 截断过长版本号，保留首尾片段便于列表展示。
     *
     * @param ver 原始版本号
     * @return 截断后的版本号，空则返回「0」
     */
    public static String shortenVer(String ver) {
        if (isEmpty(ver)) {
            return "0";
        }
        String text = ver.trim();
        int max = 28;
        if (text.length() <= max) {
            return text;
        }
        int head = 12;
        int tail = 10;
        return text.substring(0, head) + "..." + text.substring(text.length() - tail);
    }

    /**
     * 格式化上次成功同步时间为中文说明。
     *
     * @param at 上次成功时间，null 表示尚未同步
     * @return 展示用文案
     */
    public static String formatSuccessAt(LocalDateTime at) {
        if (at == null) {
            return "尚未成功同步";
        }
        return "已于 " + at.format(SUCCESS_AT_FMT) + " 同步";
    }

    /**
     * 截断过长文件名，保留首尾片段便于列表展示。
     *
     * @param filename 平台返回的文件名
     * @return 展示用文件名，空则返回「-」
     */
    public static String formatFilename(String filename) {
        if (isEmpty(filename)) {
            return "-";
        }
        String name = filename.trim();
        if (name.length() <= 48) {
            return name;
        }
        return name.substring(0, 22) + "..." + name.substring(name.length() - 20);
    }
}

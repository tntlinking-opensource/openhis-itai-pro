package com.openhis.chs.domain.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 目录下载游标/版本号 UI 展示辅助（无业务分支）。
 */
public final class CatalogDownloadDisplaySupport {

    private static final DateTimeFormatter SUCCESS_AT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CatalogDownloadDisplaySupport() {
    }

    public static String infnoLabel(String infno) {
        if (StringUtils.isBlank(infno)) {
            return "—";
        }
        return switch (infno.trim()) {
            case ChsInfNo.DRUG_CATALOG_DOWNLOAD -> "1301 西药中成药";
            case ChsInfNo.HERB_CATALOG_DOWNLOAD -> "1302 中药饮片";
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> "1305 医疗服务项目";
            case ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD -> "1306 医用耗材";
            case ChsInfNo.DISEASE_CATALOG_DOWNLOAD -> "1307 疾病与诊断";
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> "1308 手术操作";
            case ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD -> "1309 门诊慢特病";
            case ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD -> "1314 中医疾病";
            case ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD -> "1315 中医证候";
            default -> infno.trim();
        };
    }

    public static String listTypeForInfno(String infno) {
        if (StringUtils.isBlank(infno)) {
            return ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG;
        }
        return switch (infno.trim()) {
            case ChsInfNo.DRUG_CATALOG_DOWNLOAD -> ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG;
            case ChsInfNo.HERB_CATALOG_DOWNLOAD -> ChsCatalogConstants.LIST_TYPE_HERB;
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> ChsCatalogConstants.LIST_TYPE_MEDICAL_SERVICE;
            case ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD -> ChsCatalogConstants.LIST_TYPE_MEDICAL_CONSUMABLE;
            case ChsInfNo.DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> ChsInfNo.SURGERY_CATALOG_DOWNLOAD;
            case ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD -> ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD;
            default -> ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG;
        };
    }

    /** 长版本号中间省略，便于表格展示 */
    public static String shortenVer(String ver) {
        if (StringUtils.isBlank(ver)) {
            return "0";
        }
        String text = ver.trim();
        int max = 28;
        if (text.length() <= max) {
            return text;
        }
        int head = 12;
        int tail = 10;
        return text.substring(0, head) + "…" + text.substring(text.length() - tail);
    }

    public static String formatSuccessAt(LocalDateTime at) {
        if (at == null) {
            return "尚未成功同步";
        }
        return "已于 " + at.format(SUCCESS_AT_FMT) + " 同步";
    }

    public static String formatFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "—";
        }
        String name = filename.trim();
        if (name.length() <= 48) {
            return name;
        }
        return name.substring(0, 22) + "…" + name.substring(name.length() - 20);
    }
}

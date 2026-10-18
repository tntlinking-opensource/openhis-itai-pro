/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.enums.AdministrativeGender;

/**
 * 行政性别（域内 {@link AdministrativeGender}：{@code MALE}/{@code FEMALE}/{@code UNKNOWN}）出站码表集中出口。
 *
 * <p>域内业务只认 AdministrativeGender；外传对方码表不一致时，<b>仅在本类</b>按目标系统转换，
 * 禁止在业务/院点里再散落「1/2/01/02」魔法映射。</p>
 *
 * <p>展示文案请用 {@link AdministrativeGenderDisplayUtil}，不在本类。</p>
 *
 * <pre>
 * 目标系统码表（勿混用）：
 * - LIS（历史 LisPatientSex）：未知 01 / 男 02 / 女 03
 * - PACS（历史 PacsPatientSex）：男 01 / 女 02 / 未知 03
 * - 病案首页 / 医保 4401 gend：男 1 / 女 2
 * </pre>
 */
public final class AdministrativeGenderOutboundUtil {

    /** LIS 未知（LisPatientSex.UNKNOWN）。 */
    public static final String LIS_UNKNOWN = "01";
    /** LIS 男（LisPatientSex.MALE）。 */
    public static final String LIS_MALE = "02";
    /** LIS 女（LisPatientSex.FEMALE）。 */
    public static final String LIS_FEMALE = "03";

    /** PACS 男（PacsPatientSex.MALE）。 */
    public static final String PACS_MALE = "01";
    /** PACS 女（PacsPatientSex.FEMALE）。 */
    public static final String PACS_FEMALE = "02";
    /** PACS 未知（PacsPatientSex.UNKNOWN）。 */
    public static final String PACS_UNKNOWN = "03";

    /** 病案首页 / 医保 4401：男。 */
    public static final String HOMEPAGE_MALE = "1";
    /** 病案首页 / 医保 4401：女。 */
    public static final String HOMEPAGE_FEMALE = "2";

    /**
     * 禁止实例化：仅提供静态出站映射。
     */
    private AdministrativeGenderOutboundUtil() {
    }

    /**
     * 发往 LIS 申请单 {@code patientSex}（未知 01 / 男 02 / 女 03）。
     *
     * <p>空或无法识别按未知 {@link #LIS_UNKNOWN}，与历史 {@code LisPatientSex} 一致。</p>
     *
     * @param genderEnum 域内 {@code gender_enum}（{@code MALE}/{@code FEMALE}/{@code UNKNOWN}）
     * @return LIS patientSex 码
     */
    public static String toLisPatientSex(String genderEnum) {
        AdministrativeGender gender = resolveGender(genderEnum);
        if (gender == AdministrativeGender.MALE) {
            return LIS_MALE;
        }
        if (gender == AdministrativeGender.FEMALE) {
            return LIS_FEMALE;
        }
        // 空、UNKNOWN、未识别：LIS 未知
        return LIS_UNKNOWN;
    }

    /**
     * 发往 PACS 申请单 {@code patientSex}（男 01 / 女 02 / 未知 03）。
     *
     * <p>空或无法识别按未知 {@link #PACS_UNKNOWN}，与历史 {@code PacsPatientSex} 一致。</p>
     *
     * @param genderEnum 域内 {@code gender_enum}
     * @return PACS patientSex 码
     */
    public static String toPacsPatientSex(String genderEnum) {
        AdministrativeGender gender = resolveGender(genderEnum);
        if (gender == AdministrativeGender.MALE) {
            return PACS_MALE;
        }
        if (gender == AdministrativeGender.FEMALE) {
            return PACS_FEMALE;
        }
        // 空、UNKNOWN、未识别：PACS 未知
        return PACS_UNKNOWN;
    }

    /**
     * 发往病案首页 / 医保 4401 {@code gend_code}（男 1 / 女 2）。
     *
     * <p>空返回 {@code null}；已是首页码 {@code 1}/{@code 2} 则原样保留；其余未识别码原样回传（与历史 autofill 一致）。</p>
     *
     * @param genderEnum 患者 {@code gender_enum} 或已是首页码
     * @return 首页 gend_code；输入空则为 {@code null}
     */
    public static String toHomepageGenderCode(String genderEnum) {
        if (genderEnum == null || genderEnum.isBlank()) {
            return null;
        }
        String code = genderEnum.trim();
        if (AdministrativeGender.MALE.getCode().equals(code)) {
            return HOMEPAGE_MALE;
        }
        if (AdministrativeGender.FEMALE.getCode().equals(code)) {
            return HOMEPAGE_FEMALE;
        }
        // 已是 1/2 等首页码或其它透传值：不二次改写
        return code;
    }

    /**
     * 将域内 gender code 解析为枚举；空白或不认识返回 {@code null}。
     *
     * @param genderEnum 持久化 gender_enum
     * @return 命中的 {@link AdministrativeGender}，否则 {@code null}
     */
    private static AdministrativeGender resolveGender(String genderEnum) {
        if (genderEnum == null || genderEnum.isBlank()) {
            return null;
        }
        return EnumUtil.fromCode(AdministrativeGender.class, genderEnum.trim());
    }
}

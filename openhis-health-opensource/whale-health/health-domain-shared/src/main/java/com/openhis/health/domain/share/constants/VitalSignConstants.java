/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 生命体征字典项与明细通用常量（DM-DIA-002 v2.1）。
 *
 * <p>覆盖体温单（AS-IP-NUR-007）、护理记录（AS-IP-NUR-014）、入科体征（AS-IP-NUR-002）
 * 共用的 {@code vital_signs_code} / {@code special_event_code} 编码。</p>
 */
public final class VitalSignConstants {

    /** 明细自然键分隔符（{@code term_code} 与 {@code item_code} 拼接用，非展示字符）。 */
    public static final String NATURAL_KEY_SEPARATOR = "\u0001";

    // --- vital_signs_code：多场景共用体征项 ---

    /** 体温。 */
    public static final String TEMP = "TEMP";

    /** 脉搏。 */
    public static final String PULSE = "PULSE";

    /** 呼吸。 */
    public static final String RESP = "RESP";

    /** 心率。 */
    public static final String HR = "HR";

    /** 收缩压。 */
    public static final String SBP = "SBP";

    /** 舒张压。 */
    public static final String DBP = "DBP";

    /** 身高。 */
    public static final String HEIGHT = "HEIGHT";

    /** 体重。 */
    public static final String WEIGHT = "WEIGHT";

    // --- vital_signs_code：体温单补充项 ---

    /** 入量（体温单 22:00 补充记录）。 */
    public static final String INTAKE = "INTAKE";

    /** 尿量（体温单补充记录）。 */
    public static final String URINE_VOL = "URINE_VOL";

    /** 大便次数（体温单补充记录）。 */
    public static final String STOOL = "STOOL";

    /** 腹围（体温单补充记录）。 */
    public static final String WAIST = "WAIST";

    /** 小便次数（体温单补充记录）。 */
    public static final String URINE_FREQ = "URINE_FREQ";

    /** 皮试阳性（22:00 槽投影为主表 remark，读写经明细项互转）。 */
    public static final String SKIN_TEST_POSITIVE = "SKIN_TEST_POSITIVE";

    /** 皮试阳性写入主表 remark 时的前缀（与 {@link #SKIN_TEST_POSITIVE} 互转协议）。 */
    public static final String SKIN_TEST_POSITIVE_REMARK_PREFIX = "皮试阳性:";

    /** 其他（22:00 槽投影为主表 remark，读写经明细项互转）。 */
    public static final String OTHER = "OTHER";

    // --- vital_signs_code：护理记录专用项 ---

    /** 血氧饱和度。 */
    public static final String SPO2 = "SPO2";

    /** 血压（无法拆分时整段写入）。 */
    public static final String BP = "BP";

    /** 意识。 */
    public static final String CONSCIOUSNESS = "CONSCIOUSNESS";

    /** 瞳孔左。 */
    public static final String PUPIL_L = "PUPIL_L";

    /** 瞳孔右。 */
    public static final String PUPIL_R = "PUPIL_R";

    /** 吸氧方式。 */
    public static final String OXYGEN_MODE = "OXYGEN_MODE";

    /** 吸氧。 */
    public static final String OXYGEN_RATE = "OXYGEN_RATE";

    /** 氧流量。 */
    public static final String OXYGEN_FLOW = "OXYGEN_FLOW";

    /** 入量名称。 */
    public static final String INTAKE_NAME = "INTAKE_NAME";

    /** 入量途径。 */
    public static final String INTAKE_ROUTE = "INTAKE_ROUTE";

    /** 出量名称。 */
    public static final String OUTPUT_NAME = "OUTPUT_NAME";

    /** 出量值。 */
    public static final String OUTPUT = "OUTPUT";

    /** 出量颜色性状。 */
    public static final String OUTPUT_CHARACTER = "OUTPUT_CHARACTER";

    /** 皮肤情况。 */
    public static final String SKIN_STATUS = "SKIN_STATUS";

    /** 管路护理。 */
    public static final String TUBE_CARE = "TUBE_CARE";

    private static final List<String> ADMISSION_DETAIL_CODES = List.of(
        HEIGHT, WEIGHT, TEMP, HR, PULSE, SBP, DBP);

    private static final Set<String> ADMISSION_CODE_SET = Collections.unmodifiableSet(
        new LinkedHashSet<>(ADMISSION_DETAIL_CODES));

    private VitalSignConstants() {
    }

    /**
     * 返回入科明细项编码（含体温，均写明细）。
     *
     * @return 不可变列表
     */
    public static List<String> admissionDetailCodes() {
        return ADMISSION_DETAIL_CODES;
    }

    /**
     * 返回入科体征项编码全集（顺序固定，供 merge 与展示）。
     *
     * @return 不可变列表
     */
    public static List<String> admissionAllCodes() {
        return ADMISSION_DETAIL_CODES;
    }

    /**
     * 判断编码是否属于入科抽屉维护范围。
     *
     * @param code 字典项 code
     * @return 是否入科项
     */
    public static boolean isAdmissionCode(String code) {
        // 业务分支：null 编码不属于入科抽屉范围
        if (code == null) {
            return false;
        }
        return ADMISSION_CODE_SET.contains(code);
    }

    /**
     * 护理记录页涉及的 {@code vital_signs_code} 全集（IF-01 术语子集过滤用）。
     *
     * @return 不可变 code 集合
     */
    public static Set<String> nursingRecordDetailCodes() {
        Set<String> codes = new HashSet<>();
        codes.add(PULSE);
        codes.add(RESP);
        codes.add(HR);
        codes.add(SPO2);
        codes.add(SBP);
        codes.add(DBP);
        codes.add(BP);
        codes.add(CONSCIOUSNESS);
        codes.add(PUPIL_L);
        codes.add(PUPIL_R);
        codes.add(OXYGEN_MODE);
        codes.add(OXYGEN_RATE);
        codes.add(OXYGEN_FLOW);
        codes.add(INTAKE_NAME);
        codes.add(INTAKE);
        codes.add(INTAKE_ROUTE);
        codes.add(OUTPUT_NAME);
        codes.add(OUTPUT);
        codes.add(OUTPUT_CHARACTER);
        codes.add(SKIN_STATUS);
        codes.add(TUBE_CARE);
        return Collections.unmodifiableSet(codes);
    }

    /**
     * 体温单特殊事件字典项（{@code term_code=special_event_code}）。
     */
    public static final class SpecialEvent {

        /** 入院。 */
        public static final String ADMISSION = "ADMISSION";

        /** 转科。 */
        public static final String TRANSFER = "TRANSFER";

        /** 手术。 */
        public static final String SURGERY = "SURGERY";

        /** 分娩。 */
        public static final String DELIVERY = "DELIVERY";

        /** 出院。 */
        public static final String DISCHARGE = "DISCHARGE";

        /** 死亡。 */
        public static final String DEATH = "DEATH";

        /** 外出检查。 */
        public static final String OUT_FOR_EXAM = "OUT_FOR_EXAM";

        /** 拒测体温。 */
        public static final String REFUSE_TEMP = "REFUSE_TEMP";

        /** 物理降温。 */
        public static final String PHYSICAL_COOLING = "PHYSICAL_COOLING";

        /** 药物降温。 */
        public static final String MEDICATION_COOLING = "MEDICATION_COOLING";

        /** 体温不升。 */
        public static final String TEMP_NOT_RISING = "TEMP_NOT_RISING";

        /** 脉搏短绌。 */
        public static final String PULSE_DEFICIT = "PULSE_DEFICIT";

        /** 特殊事件占位值（无附加数值时）。 */
        public static final String FLAG_VALUE = "1";

        private SpecialEvent() {
        }
    }
}

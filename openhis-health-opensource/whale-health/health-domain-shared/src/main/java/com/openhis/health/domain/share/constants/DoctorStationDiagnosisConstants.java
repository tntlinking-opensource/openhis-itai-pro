/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

import java.util.List;

/**
 * 门诊医生站「诊断录入」跨层共享常量：中西医筛选入参、目录来源分组、快照空串、验证状态码等。
 *
 * <p>与 {@code DoctorStationDiagnosisMapperImpl#parseDiagnosisJson}、
 * {@code EncounterDomainService#buildEncounterDiagnosisSnapshotPayload} 契约一致；修改须双端对齐。</p>
 */
public interface DoctorStationDiagnosisConstants {

    /**
     * 医生站分页检索入参：筛选「西医」类目录来源（与术语/目录编码约定一致）。
     */
    String DIAGNOSIS_KIND_FILTER_WESTERN = "1";

    /**
     * 医生站分页检索入参：筛选「中医」类目录来源。
     */
    String DIAGNOSIS_KIND_FILTER_TCM = "2";

    /**
     * 无诊断快照时的 JSON 字面量（空数组）。
     */
    String DIAGNOSIS_SNAPSHOT_EMPTY_ARRAY = "[]";

    /**
     * 中西医维度标签：西医（持久化于快照 {@code diagnosisKind}）。
     */
    String DIAGNOSIS_KIND_TAG_WESTERN = "WESTERN";

    /**
     * 中西医维度标签：中医。
     */
    String DIAGNOSIS_KIND_TAG_TCM = "TCM";

    /**
     * 主诊标记：是（与就诊诊断关联 {@code primaryFlag} 及 UI 整型口径一致）。
     */
    int MAIN_DIAGNOSIS_FLAG_YES = 1;

    /**
     * 主诊标记：否。
     */
    int MAIN_DIAGNOSIS_FLAG_NO = 0;

    /**
     * 多条诊断合并为一行摘要时的展示分隔符（住院登记/护士站列表卡片）。
     */
    String DIAGNOSIS_DISPLAY_SUMMARY_SEPARATOR = "；";

    /**
     * 主诊断在列表/详情展示行中的短标签（前缀 {@code [主]}）。
     */
    String MAIN_DIAGNOSIS_DISPLAY_TAG = "主";

    /**
     * 目录来源枚举串：西医侧分组（含临床指南等），用于 SQL {@code IN} 与维度推导。
     */
    List<String> CONDITION_SOURCE_CODES_WESTERN = List.of("1", "2", "3", "4", "7");

    /**
     * 目录来源枚举串：中医侧分组。
     */
    List<String> CONDITION_SOURCE_CODES_TCM = List.of("5", "6");

    /**
     * 界面展示：医保目录命中。
     */
    String YB_FLAG_TEXT_YES = "是";

    /**
     * 界面展示：非医保目录或未命中。
     */
    String YB_FLAG_TEXT_NO = "否";

    /**
     * 界面展示：医保标识列「是」的持久化整型（与历史接口约定一致）。
     */
    int YB_FLAG_INT_YES = 1;

    /**
     * 界面展示：医保标识列「否」的持久化整型。
     */
    int YB_FLAG_INT_NO = 0;

    /**
     * FHIR Condition.verificationStatus 持久化码（与域命令/库列一致）。
     */
    interface ConditionVerificationStatusCode {
        String UNCONFIRMED = "UNCONFIRMED";
        String PROVISIONAL = "PROVISIONAL";
        String DIFFERENTIAL = "DIFFERENTIAL";
        String CONFIRMED = "CONFIRMED";
        String REFUTED = "REFUTED";
        String ENTERED_IN_ERROR = "ENTERED_IN_ERROR";
    }

    /**
     * 医生站 UI 层验证状态整数码（与前端表格/快照 JSON 一致）。
     */
    interface VerificationUiCode {
        int UNCONFIRMED = 1;
        int PROVISIONAL = 2;
        int DIFFERENTIAL = 3;
        int CONFIRMED = 4;
        int REFUTED = 5;
        int ENTERED_IN_ERROR = 6;
    }
}

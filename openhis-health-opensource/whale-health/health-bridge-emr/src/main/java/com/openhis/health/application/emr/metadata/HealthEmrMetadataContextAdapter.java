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

package com.openhis.health.application.emr.metadata;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.repository.PractitionerRepository;
import com.openhis.whale.emr.domain.metadata.port.EmrMetadataContextPort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.Optional;

/**
 * health 模块对 {@link EmrMetadataContextPort} 的实现：将患者主数据、就诊主数据及诊断摘要写入 EMR 元数据扁平上下文。
 *
 * <p>调用链：{@code EncounterMetadataAppService} / {@code MetadataValueResolver}
 * → {@code EmrMetadataContextBuilder#build} → 本适配器 → {@code MetadataValueResolver#resolveFromPath}。</p>
 *
 * <p>键名与《EMR 元数据字段定义与维护》§3.3 {@code source_path} 对齐（如 {@code patient.name}、
 * {@code diagnosis.primaryDisplay}）。诊断类键由 {@link EmrDiagnosisMetadataContributor} 贡献，读模型
 * {@code diagnosisJson} 仅在应用层解析，<b>禁止</b>向 HTTP 透出原始 JSON（B.6.2）。</p>
 *
 * <p>边界：就诊科室展示名 {@code encounter.deptName} / {@code dept.name} 当前回填为组织主键字符串，
 * 待组织/科室读模型对接后替换；无效 {@code patientId}/{@code encounterId} 静默跳过，不抛异常，
 * 由字段定义的 {@code default_value} 兜底。</p>
 */
@Component
public class HealthEmrMetadataContextAdapter implements EmrMetadataContextPort {

    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;
    private final PractitionerRepository practitionerRepository;
    private final EmrDiagnosisMetadataContributor diagnosisMetadataContributor;

    public HealthEmrMetadataContextAdapter(
            PatientRepository patientRepository,
            EncounterRepository encounterRepository,
            PractitionerRepository practitionerRepository,
            EmrDiagnosisMetadataContributor diagnosisMetadataContributor) {
        this.patientRepository = patientRepository;
        this.encounterRepository = encounterRepository;
        this.practitionerRepository = practitionerRepository;
        this.diagnosisMetadataContributor = diagnosisMetadataContributor;
    }

    /**
     * 向目标 Map 写入患者维度扁平键（{@code patient.*}）。
     *
     * @param target 元数据解析上下文，由 EMR 侧创建；本方法仅追加键值
     * @param patientId 患者主键字符串；非数字或不存在时不写入
     */
    @Override
    public void contributePatient(Map<String, String> target, String patientId) {
        Optional<Long> patientPk = parseLongId(patientId);
        if (patientPk.isEmpty()) {
            return;
        }
        // 患者不存在时静默跳过（医生站未选患者或 ID 过期），不抛错阻断文书打开
        patientRepository.findById(patientPk.get()).ifPresent(p -> mapPatient(target, p));
    }

    /**
     * 向目标 Map 写入就诊维度扁平键（{@code encounter.*}、{@code insurance.*}）及诊断摘要（{@code diagnosis.*}）。
     *
     * @param target 元数据解析上下文
     * @param encounterId 就诊主键字符串；非数字或不存在时不写入
     */
    @Override
    public void contributeEncounter(Map<String, String> target, String encounterId) {
        Optional<Long> encounterPk = parseLongId(encounterId);
        if (encounterPk.isEmpty()) {
            return;
        }
        Optional<Encounter> encounterOpt = encounterRepository.findById(encounterPk.get());
        if (encounterOpt.isEmpty()) {
            // 就诊行未落库或 ID 与当前会话不一致：不写 encounter.* / diagnosis.*
            return;
        }
        Encounter encounter = encounterOpt.get();
        mapEncounter(target, encounter);
        diagnosisMetadataContributor.contribute(target, encounter);
    }

    /**
     * 向目标 Map 写入医务人员主档键（{@code practitioner.*}），供 USER 别名 {@code user.signatureImage} 等解析。
     */
    @Override
    public void contributePractitioner(Map<String, String> target, Long practitionerId) {
        if (practitionerId == null) {
            return;
        }
        practitionerRepository.findById(practitionerId).ifPresent(p -> mapPractitioner(target, p));
    }

    /**
     * 将 {@link Patient} 字段映射为 {@code patient.*} 路径；年龄按出生日期与当前日期整年差计算。
     *
     * @param target 上下文 Map
     * @param patient 已加载的患者实体
     */
    private void mapPatient(Map<String, String> target, Patient patient) {
        put(target, "patient.id", String.valueOf(patient.getId()));
        put(target, "patient.name", patient.getName());
        // 性别存枚举 code 字符串，与元数据 DICT/PATIENT 解析约定一致
        put(target, "patient.gender", patient.getGenderEnum());
        put(target, "patient.medicalRecordNo", patient.getBusNo());
        put(target, "patient.idCard", patient.getIdCard());
        put(target, "patient.phone", patient.getPhone());
        if (patient.getBirthDate() != null) {
            put(target, "patient.birthDate", patient.getBirthDate().toString());
            int years = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            // 未来出生日期等异常数据不写 age，避免负值进入页眉
            if (years >= 0) {
                put(target, "patient.age", String.valueOf(years));
            }
        }
    }

    /**
     * 将 {@link Encounter} 字段映射为 {@code encounter.*} 及关联别名键。
     *
     * @param target 上下文 Map
     * @param encounter 已加载的就诊实体
     */
    private void mapPractitioner(Map<String, String> target, Practitioner practitioner) {
        put(target, "practitioner.id", String.valueOf(practitioner.getId()));
        put(target, "practitioner.name", practitioner.getName());
        put(target, "practitioner.busNo", practitioner.getBusNo());
        String signatureImage = normalizeSignatureDataUrl(practitioner.getSignature());
        if (signatureImage != null) {
            put(target, "practitioner.signatureImage", signatureImage);
            // 与元数据维护页 USER 来源别名键对齐
            put(target, "user.signatureImage", signatureImage);
        }
    }

    /**
     * 将库内签名（纯 Base64 或 data URL）规范为可嵌入 PDF/前端的 data URL。
     */
    private static String normalizeSignatureDataUrl(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        return "data:image/png;base64," + trimmed;
    }

    private void mapEncounter(Map<String, String> target, Encounter encounter) {
        put(target, "encounter.id", String.valueOf(encounter.getId()));
        put(target, "encounter.busNo", encounter.getBusNo());
        if (encounter.getOrganizationId() != null) {
            String orgIdText = String.valueOf(encounter.getOrganizationId());
            put(target, "encounter.organizationId", orgIdText);
            // DOC-CONFLICT：科室展示名暂用组织 ID 占位，待科室读模型/组织名称解析接入后替换
            put(target, "encounter.deptName", orgIdText);
            // 与元数据维护页 dept.name 别名键兼容（同一占位值）
            put(target, "dept.name", orgIdText);
        }
        if (encounter.getStartTime() != null) {
            String startText = encounter.getStartTime().toString();
            put(target, "encounter.startTime", startText);
            // 住院登记场景与 encounter.startTime 共用同一时刻文本
            put(target, "encounter.admissionTime", startText);
        }
        if (encounter.getClassEnum() != null) {
            // 就诊类别 code（AMB/EMER/IMP），供 EXPR 或后续扩展分支使用
            put(target, "encounter.classEnum", encounter.getClassEnum());
        }
        if (encounter.getYbClassText() != null) {
            put(target, "insurance.payorName", encounter.getYbClassText());
        }
    }

    /**
     * 将 EMR 侧传入的字符串主键解析为 Long；解析失败返回空，由调用方决定是否跳过贡献。
     *
     * @param id 患者或就诊 ID 字符串
     * @return 可解析时的 Long；空白或非法格式为 empty
     */
    private static Optional<Long> parseLongId(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(id.trim()));
        } catch (NumberFormatException ex) {
            // 元数据解析为尽力而为：非法 ID 不阻断其它来源字段
            return Optional.empty();
        }
    }

    /**
     * 仅当值非空白时写入上下文，统一 trim，避免页眉出现首尾空格。
     *
     * @param target 上下文 Map
     * @param key 点分路径键
     * @param value 待写入的展示值
     */
    private static void put(Map<String, String> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }
}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientInpatientProjectionSnapshot;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * 住院就诊上下文队列行的展示回填（登记/预交金/护士站共用）。
 *
 * <p>主数据由 {@link com.openhis.health.domain.administration.encounter.service.EncounterQueryService}
 * 批量 enrichment；本类仅对<strong>仍为空白</strong>的列从患者/科室/位置主档补齐，不写库。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientInfoQueueDisplayAssembler {

    /** 患者读侧查询（住院投影快照与姓名批量索引）。 */
    private final PatientQueryService patientQueryService;
    /** 机构读侧查询（科室名称批量索引）。 */
    private final OrganizationQueryService organizationQueryService;
    /** 位置读侧查询（病区/床位名称批量索引）。 */
    private final LocationQueryService locationQueryService;
    /** 就诊读侧查询（业务号批量索引）。 */
    private final EncounterQueryService encounterQueryService;
    /** 合同读侧查询（费用性质/合同展示名）。 */
    private final ContractQueryService contractQueryService;

    /**
     * 为当前页住院上下文行构建展示覆盖层。
     *
     * @param rows 就诊上下文读模型行（当前页）
     * @return 就诊主键 → 展示覆盖层；无行时 empty
     */
    public Map<Long, InpatientQueueDisplayOverlay> buildOverlay(List<InpatientEncounterContextQueryModel> rows) {
        return this.buildOverlay(rows, Collections.emptyMap());
    }

    /**
     * 构建展示覆盖层，并附带账户合同号解析费用性质展示（预交金队列）。
     *
     * @param rows                     就诊上下文读模型行
     * @param contractNoByEncounterId  就诊 → 账户合同号；预交金队列传入，其它队列可 empty
     * @return 就诊主键 → 展示覆盖层
     */
    public Map<Long, InpatientQueueDisplayOverlay> buildOverlay(
        List<InpatientEncounterContextQueryModel> rows,
        Map<Long, String> contractNoByEncounterId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        // Pre：上下文行转为可变副本，便于就地补齐仍为空白的展示列
        List<MutableInpatientQueueRow> mutableRows = new ArrayList<>(rows.size());
        for (InpatientEncounterContextQueryModel row : rows) {
            mutableRows.add(MutableInpatientQueueRow.fromContext(row));
        }
        return this.buildOverlayInternal(mutableRows, contractNoByEncounterId);
    }

    /**
     * 住院队列展示 overlay：批量解析患者/机构/库位/合同等主数据并写回可变行。
     *
     * @param rows                     可变队列行
     * @param contractNoByEncounterId  就诊 → 合同号（可空）
     * @return encounterId → overlay
     */
    private Map<Long, InpatientQueueDisplayOverlay> buildOverlayInternal(
        List<MutableInpatientQueueRow> rows,
        Map<Long, String> contractNoByEncounterId) {
        Set<Long> patientIds = new LinkedHashSet<>();
        Set<Long> organizationIds = new LinkedHashSet<>();
        Set<Long> locationIds = new LinkedHashSet<>();
        Set<Long> encounterIds = new LinkedHashSet<>();
        Set<String> contractBusNos = new LinkedHashSet<>();
        for (MutableInpatientQueueRow row : rows) {
            if (row == null) {
                continue;
            }
            if (row.patientId != null) {
                patientIds.add(row.patientId);
            }
            if (row.organizationId != null) {
                organizationIds.add(row.organizationId);
            }
            if (row.wardId != null) {
                locationIds.add(row.wardId);
            }
            if (row.bedId != null) {
                locationIds.add(row.bedId);
            }
            if (row.encounterId != null) {
                encounterIds.add(row.encounterId);
            }
            if (contractNoByEncounterId != null && row.encounterId != null) {
                String contractNo = contractNoByEncounterId.get(row.encounterId);
                if (StringUtils.isNotEmpty(contractNo)) {
                    contractBusNos.add(contractNo.trim());
                }
            }
        }
        Map<Long, PatientInpatientProjectionSnapshot> patientSnapshotById =
            patientQueryService.mapInpatientProjectionSnapshotsByIds(patientIds);
        Map<Long, String> patientNameById = patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);
        Map<Long, String> busNoByEncounterId = encounterQueryService.mapBusNoByEncounterIds(encounterIds);
        Map<String, String> contractNameByBusNo = contractQueryService.mapContractNameByBusNoIn(contractBusNos);
        Map<Long, InpatientQueueDisplayOverlay> overlayByEncounterId = new HashMap<>(rows.size() * 2);
        // Asm：逐行仅对仍为空白的主数据列写覆盖层，不覆盖 EncounterQueryService 已 enrichment 的字段
        for (MutableInpatientQueueRow row : rows) {
            if (row == null || row.encounterId == null) {
                continue;
            }
            InpatientQueueDisplayOverlay overlay = new InpatientQueueDisplayOverlay();
            if (row.patientId != null) {
                PatientInpatientProjectionSnapshot snapshot = patientSnapshotById.get(row.patientId);
                if (snapshot != null) {
                    // 行内可变副本：优先用住院投影补齐姓名/性别/出生/年龄
                    if (StringUtils.isEmpty(row.patientName) && StringUtils.isNotEmpty(snapshot.getPatientName())) {
                        row.patientName = snapshot.getPatientName();
                    }
                    if (StringUtils.isEmpty(row.genderEnum) && StringUtils.isNotEmpty(snapshot.getGenderEnum())) {
                        row.genderEnum = snapshot.getGenderEnum();
                    }
                    if (row.birthDate == null && snapshot.getBirthDate() != null) {
                        row.birthDate = snapshot.getBirthDate();
                    }
                    if (StringUtils.isEmpty(row.ageText) && StringUtils.isNotEmpty(snapshot.getAgeText())) {
                        row.ageText = snapshot.getAgeText();
                    }
                }
                // 覆盖层：上下文仍缺患者姓名时回落投影或批量姓名索引
                if (StringUtils.isEmpty(row.patientName)) {
                    if (snapshot != null && StringUtils.isNotEmpty(snapshot.getPatientName())) {
                        overlay.setPatientName(snapshot.getPatientName());
                    } else {
                        overlay.setPatientName(patientNameById.get(row.patientId));
                    }
                }
                if (StringUtils.isEmpty(row.genderEnum) && snapshot != null
                    && StringUtils.isNotEmpty(snapshot.getGenderEnum())) {
                    overlay.setGenderEnum(snapshot.getGenderEnum());
                }
                if (row.birthDate == null && snapshot != null && snapshot.getBirthDate() != null) {
                    overlay.setBirthDate(snapshot.getBirthDate());
                }
                if (StringUtils.isEmpty(row.ageText) && snapshot != null
                    && StringUtils.isNotEmpty(snapshot.getAgeText())) {
                    overlay.setAgeText(snapshot.getAgeText());
                }
            }
            if (StringUtils.isEmpty(row.organizationName) && row.organizationId != null) {
                overlay.setOrganizationName(organizationNameById.get(row.organizationId));
            }
            if (StringUtils.isEmpty(row.wardName) && row.wardId != null) {
                overlay.setWardName(locationNameById.get(row.wardId));
            }
            if (StringUtils.isEmpty(row.bedName) && row.bedId != null) {
                overlay.setBedName(locationNameById.get(row.bedId));
            }
            if (StringUtils.isEmpty(row.encounterBusNo)) {
                overlay.setEncounterBusNo(busNoByEncounterId.get(row.encounterId));
            }
            if (StringUtils.isEmpty(row.statusEnumText) && StringUtils.isNotEmpty(row.statusEnum)) {
                // 住院状态展示须结合转科/出院类型解析中文文案
                overlay.setStatusEnumText(InpatientEncounterStatusDisplayUtil.resolveDisplayText(
                    row.statusEnum, row.transferOrgId, row.dischargeTypeEnum));
            }
            if (contractNoByEncounterId != null) {
                String contractNo = contractNoByEncounterId.get(row.encounterId);
                // 预交金队列：合同号 → 费用性质展示名
                if (StringUtils.isNotEmpty(contractNo)) {
                    overlay.setContractDisplay(contractNameByBusNo.get(contractNo.trim()));
                }
            }
            overlayByEncounterId.put(row.encounterId, overlay);
        }
        return overlayByEncounterId;
    }

    /** 可变队列行副本：承载上下文原值并在装配过程中就地补齐。 */
    private static final class MutableInpatientQueueRow {
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 科室主键。 */
        private Long organizationId;
        /** 病区位置主键。 */
        private Long wardId;
        /** 床位位置主键。 */
        private Long bedId;
        /** 转科目标机构主键（状态展示用）。 */
        private Long transferOrgId;
        /** 患者姓名（上下文原值）。 */
        private String patientName;
        /** 性别枚举 code。 */
        private String genderEnum;
        /** 出生日期时间。 */
        private LocalDateTime birthDate;
        /** 年龄展示文本。 */
        private String ageText;
        /** 科室名称（上下文原值）。 */
        private String organizationName;
        /** 病区名称（上下文原值）。 */
        private String wardName;
        /** 床位名称（上下文原值）。 */
        private String bedName;
        /** 就诊业务号（上下文原值）。 */
        private String encounterBusNo;
        /** 就诊状态枚举 code。 */
        private String statusEnum;
        /** 状态展示文本（上下文原值）。 */
        private String statusEnumText;
        /** 出院类型枚举 code（状态展示用）。 */
        private String dischargeTypeEnum;

        /**
         * 就诊上下文 → 可变队列行副本（保留原展示列供就地 enrich）。
         *
         * @param row 上下文行
         * @return 可变副本；null 输入返回空对象
         */
        private static MutableInpatientQueueRow fromContext(InpatientEncounterContextQueryModel row) {
            MutableInpatientQueueRow copy = new MutableInpatientQueueRow();
            if (row == null) {
                return copy;
            }
            copy.encounterId = row.getEncounterId();
            copy.patientId = row.getPatientId();
            copy.organizationId = row.getOrganizationId();
            copy.wardId = row.getWardId();
            copy.bedId = row.getBedId();
            copy.transferOrgId = row.getTransferOrgId();
            copy.patientName = row.getPatientName();
            copy.genderEnum = row.getGenderEnum();
            copy.birthDate = row.getBirthDate();
            copy.ageText = row.getAgeText();
            copy.organizationName = row.getOrganizationName();
            copy.wardName = row.getWardName();
            copy.bedName = row.getBedName();
            copy.encounterBusNo = row.getEncounterBusNo();
            copy.statusEnum = row.getStatusEnum();
            copy.statusEnumText = row.getStatusEnumText();
            copy.dischargeTypeEnum = row.getDischargeTypeEnum();
            return copy;
        }
    }

    /**
     * 单条队列行的展示覆盖（列表/卡片装配用）。
     */
    public static final class InpatientQueueDisplayOverlay {

        /** 患者姓名覆盖值。 */
        private String patientName;
        /** 性别枚举 code 覆盖值。 */
        private String genderEnum;
        /** 出生日期时间覆盖值。 */
        private LocalDateTime birthDate;
        /** 年龄展示文本覆盖值。 */
        private String ageText;
        /** 科室名称覆盖值。 */
        private String organizationName;
        /** 病区名称覆盖值。 */
        private String wardName;
        /** 床位名称覆盖值。 */
        private String bedName;
        /** 就诊业务号覆盖值。 */
        private String encounterBusNo;
        /** 住院状态展示文本覆盖值。 */
        private String statusEnumText;
        /** 费用性质/合同展示名（预交金队列）。 */
        private String contractDisplay;

        /** @return 患者姓名覆盖值。 */
        public String getPatientName() {
            return patientName;
        }

        /** @param patientName 患者姓名覆盖值。 */
        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        /** @return 性别枚举 code 覆盖值。 */
        public String getGenderEnum() {
            return genderEnum;
        }

        /** @param genderEnum 性别枚举 code 覆盖值。 */
        public void setGenderEnum(String genderEnum) {
            this.genderEnum = genderEnum;
        }

        /** @return 出生日期时间覆盖值。 */
        public LocalDateTime getBirthDate() {
            return birthDate;
        }

        /** @param birthDate 出生日期时间覆盖值。 */
        public void setBirthDate(LocalDateTime birthDate) {
            this.birthDate = birthDate;
        }

        /** @return 年龄展示文本覆盖值。 */
        public String getAgeText() {
            return ageText;
        }

        /** @param ageText 年龄展示文本覆盖值。 */
        public void setAgeText(String ageText) {
            this.ageText = ageText;
        }

        /** @return 科室名称覆盖值。 */
        public String getOrganizationName() {
            return organizationName;
        }

        /** @param organizationName 科室名称覆盖值。 */
        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }

        /** @return 病区名称覆盖值。 */
        public String getWardName() {
            return wardName;
        }

        /** @param wardName 病区名称覆盖值。 */
        public void setWardName(String wardName) {
            this.wardName = wardName;
        }

        /** @return 床位名称覆盖值。 */
        public String getBedName() {
            return bedName;
        }

        /** @param bedName 床位名称覆盖值。 */
        public void setBedName(String bedName) {
            this.bedName = bedName;
        }

        /** @return 就诊业务号覆盖值。 */
        public String getEncounterBusNo() {
            return encounterBusNo;
        }

        /** @param encounterBusNo 就诊业务号覆盖值。 */
        public void setEncounterBusNo(String encounterBusNo) {
            this.encounterBusNo = encounterBusNo;
        }

        /** @return 住院状态展示文本覆盖值。 */
        public String getStatusEnumText() {
            return statusEnumText;
        }

        /** @param statusEnumText 住院状态展示文本覆盖值。 */
        public void setStatusEnumText(String statusEnumText) {
            this.statusEnumText = statusEnumText;
        }

        /** @return 费用性质/合同展示名。 */
        public String getContractDisplay() {
            return contractDisplay;
        }

        /** @param contractDisplay 费用性质/合同展示名。 */
        public void setContractDisplay(String contractDisplay) {
            this.contractDisplay = contractDisplay;
        }
    }
}

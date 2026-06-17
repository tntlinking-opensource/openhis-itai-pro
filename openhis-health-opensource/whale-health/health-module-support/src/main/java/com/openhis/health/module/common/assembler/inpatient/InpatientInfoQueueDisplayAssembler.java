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

package com.openhis.health.module.common.assembler.inpatient;

import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientInpatientProjectionSnapshot;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import org.apache.commons.lang3.StringUtils;
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
public class InpatientInfoQueueDisplayAssembler {

    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ContractQueryService contractQueryService;

    public InpatientInfoQueueDisplayAssembler(
        PatientQueryService patientQueryService,
        OrganizationQueryService organizationQueryService,
        LocationQueryService locationQueryService,
        EncounterQueryService encounterQueryService,
        ContractQueryService contractQueryService) {
        this.patientQueryService = patientQueryService;
        this.organizationQueryService = organizationQueryService;
        this.locationQueryService = locationQueryService;
        this.encounterQueryService = encounterQueryService;
        this.contractQueryService = contractQueryService;
    }

    /**
     * 为当前页住院上下文行构建展示覆盖层。
     */
    public Map<Long, InpatientQueueDisplayOverlay> buildOverlay(List<InpatientEncounterContextQueryModel> rows) {
        return this.buildOverlay(rows, Collections.emptyMap());
    }

    /**
     * 构建展示覆盖层，并附带账户合同号解析费用性质展示（预交金队列）。
     */
    public Map<Long, InpatientQueueDisplayOverlay> buildOverlay(
        List<InpatientEncounterContextQueryModel> rows,
        Map<Long, String> contractNoByEncounterId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MutableInpatientQueueRow> mutableRows = new ArrayList<>(rows.size());
        for (InpatientEncounterContextQueryModel row : rows) {
            mutableRows.add(MutableInpatientQueueRow.fromContext(row));
        }
        return this.buildOverlayInternal(mutableRows, contractNoByEncounterId);
    }

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
                if (StringUtils.isNotBlank(contractNo)) {
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
        for (MutableInpatientQueueRow row : rows) {
            if (row == null || row.encounterId == null) {
                continue;
            }
            InpatientQueueDisplayOverlay overlay = new InpatientQueueDisplayOverlay();
            if (row.patientId != null) {
                PatientInpatientProjectionSnapshot snapshot = patientSnapshotById.get(row.patientId);
                if (snapshot != null) {
                    if (StringUtils.isBlank(row.patientName) && StringUtils.isNotBlank(snapshot.getPatientName())) {
                        row.patientName = snapshot.getPatientName();
                    }
                    if (StringUtils.isBlank(row.genderEnum) && StringUtils.isNotBlank(snapshot.getGenderEnum())) {
                        row.genderEnum = snapshot.getGenderEnum();
                    }
                    if (row.birthDate == null && snapshot.getBirthDate() != null) {
                        row.birthDate = snapshot.getBirthDate();
                    }
                    if (StringUtils.isBlank(row.ageText) && StringUtils.isNotBlank(snapshot.getAgeText())) {
                        row.ageText = snapshot.getAgeText();
                    }
                }
                if (StringUtils.isBlank(row.patientName)) {
                    if (snapshot != null && StringUtils.isNotBlank(snapshot.getPatientName())) {
                        overlay.setPatientName(snapshot.getPatientName());
                    } else {
                        overlay.setPatientName(patientNameById.get(row.patientId));
                    }
                }
                if (StringUtils.isBlank(row.genderEnum) && snapshot != null
                    && StringUtils.isNotBlank(snapshot.getGenderEnum())) {
                    overlay.setGenderEnum(snapshot.getGenderEnum());
                }
                if (row.birthDate == null && snapshot != null && snapshot.getBirthDate() != null) {
                    overlay.setBirthDate(snapshot.getBirthDate());
                }
                if (StringUtils.isBlank(row.ageText) && snapshot != null
                    && StringUtils.isNotBlank(snapshot.getAgeText())) {
                    overlay.setAgeText(snapshot.getAgeText());
                }
            }
            if (StringUtils.isBlank(row.organizationName) && row.organizationId != null) {
                overlay.setOrganizationName(organizationNameById.get(row.organizationId));
            }
            if (StringUtils.isBlank(row.wardName) && row.wardId != null) {
                overlay.setWardName(locationNameById.get(row.wardId));
            }
            if (StringUtils.isBlank(row.bedName) && row.bedId != null) {
                overlay.setBedName(locationNameById.get(row.bedId));
            }
            if (StringUtils.isBlank(row.encounterBusNo)) {
                overlay.setEncounterBusNo(busNoByEncounterId.get(row.encounterId));
            }
            if (StringUtils.isBlank(row.statusEnumText) && StringUtils.isNotBlank(row.statusEnum)) {
                overlay.setStatusEnumText(InpatientEncounterStatusDisplayUtil.resolveDisplayText(
                    row.statusEnum, row.transferOrgId, row.dischargeTypeEnum));
            }
            if (contractNoByEncounterId != null) {
                String contractNo = contractNoByEncounterId.get(row.encounterId);
                if (StringUtils.isNotBlank(contractNo)) {
                    overlay.setContractDisplay(contractNameByBusNo.get(contractNo.trim()));
                }
            }
            overlayByEncounterId.put(row.encounterId, overlay);
        }
        return overlayByEncounterId;
    }

    private static final class MutableInpatientQueueRow {
        private Long encounterId;
        private Long patientId;
        private Long organizationId;
        private Long wardId;
        private Long bedId;
        private Long transferOrgId;
        private String patientName;
        private String genderEnum;
        private LocalDateTime birthDate;
        private String ageText;
        private String organizationName;
        private String wardName;
        private String bedName;
        private String encounterBusNo;
        private String statusEnum;
        private String statusEnumText;
        private String dischargeTypeEnum;

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

        private String patientName;
        private String genderEnum;
        private LocalDateTime birthDate;
        private String ageText;
        private String organizationName;
        private String wardName;
        private String bedName;
        private String encounterBusNo;
        private String statusEnumText;
        private String contractDisplay;

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        public String getGenderEnum() {
            return genderEnum;
        }

        public void setGenderEnum(String genderEnum) {
            this.genderEnum = genderEnum;
        }

        public LocalDateTime getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(LocalDateTime birthDate) {
            this.birthDate = birthDate;
        }

        public String getAgeText() {
            return ageText;
        }

        public void setAgeText(String ageText) {
            this.ageText = ageText;
        }

        public String getOrganizationName() {
            return organizationName;
        }

        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }

        public String getWardName() {
            return wardName;
        }

        public void setWardName(String wardName) {
            this.wardName = wardName;
        }

        public String getBedName() {
            return bedName;
        }

        public void setBedName(String bedName) {
            this.bedName = bedName;
        }

        public String getEncounterBusNo() {
            return encounterBusNo;
        }

        public void setEncounterBusNo(String encounterBusNo) {
            this.encounterBusNo = encounterBusNo;
        }

        public String getStatusEnumText() {
            return statusEnumText;
        }

        public void setStatusEnumText(String statusEnumText) {
            this.statusEnumText = statusEnumText;
        }

        public String getContractDisplay() {
            return contractDisplay;
        }

        public void setContractDisplay(String contractDisplay) {
            this.contractDisplay = contractDisplay;
        }
    }
}

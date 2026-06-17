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

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院退药明细读组装：待退/已退发放事实 + 原药嘱 + 药品定义 + 开立医生（AS-IP-PHA-003 IF-IPR-03）。
 *
 * <p>退药与原正向发药单<strong>无</strong>读写关联：待退 REFUND 行在 {@code createPendingRefundDispense} 时已复制批号/追溯码；
 * 开立医生等信息经行上 {@code med_req_id}（原药嘱）解析。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientReturnMedicineDetailLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 查询住院退药明细行列表。
     *
     * @param query 就诊主键与可选退药状态过滤
     * @return 明细行；就诊为空时返回空列表
     */
    public List<ReturnMedicineDetailReadRow> listReturnMedicineDetails(ReturnMedicineDetailQuery query) {
        if (query == null || query.getEncounterId() == null) {
            return new ArrayList<>();
        }
        List<MedicationDispense> filtered = this.medicationDispenseQueryService.listInpatientReturnMedicationDispensesForDetail(
            query.getEncounterId(),
            query.getRefundStatus());
        if (filtered.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        for (MedicationDispense md : filtered) {
            if (md.getMedReqId() != null) {
                medReqIds.add(md.getMedReqId());
            }
        }
        Map<Long, MedicationRequest> medReqById = this.medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        Set<Long> practitionerIds = new HashSet<>();
        for (MedicationRequest req : medReqById.values()) {
            this.collectRequesterId(practitionerIds, req);
        }
        Map<Long, Practitioner> practitionerById = this.loadPractitioners(practitionerIds);
        Map<Long, MedicationDefinition> medicationById = this.loadMedicationDefinitions(filtered);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<ReturnMedicineDetailReadRow> rows = new ArrayList<>();
        for (MedicationDispense md : filtered) {
            ReturnMedicineDetailReadRow row = new ReturnMedicineDetailReadRow();
            row.setDispenseId(md.getId());
            row.setRequestId(md.getMedReqId());
            row.setItemId(md.getMedicationId());
            row.setItemName(this.resolveMedicationName(medicationById, md.getMedicationId()));
            row.setLotNumber(this.resolveLotNumber(md));
            row.setQuantity(this.absReturnMedicineQuantity(md.getDispenseQuantity(), md.getQuantity()));
            String unitCode = StringUtils.defaultString(md.getUnitCode());
            row.setUnitCode(unitCode);
            row.setUnitCodeText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, unitCode));
            row.setDoctorName(this.resolveDoctorName(practitionerById, medReqById.get(md.getMedReqId())));
            DispenseStatus st = md.getStatusEnum();
            if (st != null) {
                row.setRefundEnum(st.getCode());
                row.setRefundEnumText(st.getDisplay());
            } else {
                row.setRefundEnum(GeneralSharedConstants.EMPTY_STRING);
                row.setRefundEnumText(GeneralSharedConstants.EMPTY_STRING);
            }
            row.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
            row.setTraceNo(this.resolveTraceNo(md));
            rows.add(row);
        }
        return rows;
    }

    /** 收集开立医生主键。 */
    private void collectRequesterId(Set<Long> practitionerIds, MedicationRequest req) {
        if (req != null && req.getRequesterId() != null) {
            practitionerIds.add(req.getRequesterId());
        }
    }

    /** 批号：取自待退/已退发放行（创建待退时已从原单复制）。 */
    private String resolveLotNumber(MedicationDispense current) {
        if (current != null && StringUtils.isNotBlank(current.getLotNumber())) {
            return current.getLotNumber().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 追溯码：规则同 {@link #resolveLotNumber}。 */
    private String resolveTraceNo(MedicationDispense current) {
        if (current != null && StringUtils.isNotBlank(current.getTraceNo())) {
            return current.getTraceNo().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 开立医生展示名。 */
    private String resolveDoctorName(Map<Long, Practitioner> practitionerById, MedicationRequest originReq) {
        Long requesterId = null;
        if (originReq != null) {
            requesterId = originReq.getRequesterId();
        }
        return this.resolvePractitionerName(practitionerById, requesterId);
    }

    /** 退药数量展示绝对值。 */
    private BigDecimal absReturnMedicineQuantity(BigDecimal dispenseQuantity, BigDecimal quantity) {
        BigDecimal base = dispenseQuantity;
        if (base == null) {
            base = quantity;
        }
        if (base == null) {
            return BigDecimal.ZERO;
        }
        return base.abs();
    }

    /** 批量加载医务人员。 */
    private Map<Long, Practitioner> loadPractitioners(Set<Long> ids) {
        Map<Long, Practitioner> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Practitioner row : this.practitionerQueryService.findAllByIds(ids)) {
            if (row != null && row.getId() != null) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /** 批量加载药品定义。 */
    private Map<Long, MedicationDefinition> loadMedicationDefinitions(List<MedicationDispense> dispenses) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MedicationDispense md : dispenses) {
            if (md != null && md.getMedicationId() != null) {
                ids.add(md.getMedicationId());
            }
        }
        Map<Long, MedicationDefinition> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (MedicationDefinition med : this.medicationDefinitionQueryService.findAllByIds(ids)) {
            if (med != null && med.getId() != null) {
                map.put(med.getId(), med);
            }
        }
        return map;
    }

    /** 药品名称回落。 */
    private String resolveMedicationName(Map<Long, MedicationDefinition> medicationById, Long medicationId) {
        if (medicationId == null || medicationById == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        MedicationDefinition med = medicationById.get(medicationId);
        if (med != null && StringUtils.isNotBlank(med.getName())) {
            return med.getName();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 医生姓名回落。 */
    private String resolvePractitionerName(Map<Long, Practitioner> practitionerById, Long practitionerId) {
        if (practitionerId == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        Practitioner p = practitionerById.get(practitionerId);
        if (p != null && StringUtils.isNotBlank(p.getName())) {
            return p.getName();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }
}

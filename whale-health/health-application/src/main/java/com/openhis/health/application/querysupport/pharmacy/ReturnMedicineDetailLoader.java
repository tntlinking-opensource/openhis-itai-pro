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

package com.openhis.health.application.querysupport.pharmacy;

import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
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
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
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
 * 门诊退药明细读组装：发放事实 + 药品申请 + 药品定义 + 开立医生（B.6.1 跨聚合编排）。
 *
 * <p>仅注入各 DM {@code *QueryService}，批量 {@code findAllByIds} / {@code mapEntitiesByIds}，
 * 禁止循环单条仓储 I/O（A.9.1）。批号/追溯码优先当前退药发放行，回落原正向收费锚点发放。</p>
 */
@Component
@RequiredArgsConstructor
public class ReturnMedicineDetailLoader {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 查询退药明细行列表（替代领域层跨聚合装配）。
     *
     * @param query 就诊主键与可选退药状态过滤
     * @return 明细行；就诊为空时返回空列表
     */
    public List<ReturnMedicineDetailReadRow> listReturnMedicineDetails(ReturnMedicineDetailQuery query) {
        if (query == null || query.getEncounterId() == null) {
            return new ArrayList<>();
        }
        // Pre：按就诊 + 可选退药状态加载门诊退药发放事实
        List<MedicationDispense> filtered = this.medicationDispenseQueryService.listOutpatientReturnMedicationDispensesForDetail(
            query.getEncounterId(),
            query.getRefundStatus());
        if (filtered.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        Set<Long> originMedReqIds = new LinkedHashSet<>();
        for (MedicationDispense md : filtered) {
            if (md.getMedReqId() != null) {
                medReqIds.add(md.getMedReqId());
            }
        }
        // Pre：批量加载退药医嘱，并收集原正向医嘱主键以解析收费锚点发放
        Map<Long, MedicationRequest> medReqById = this.medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        for (MedicationRequest req : medReqById.values()) {
            if (req == null) {
                continue;
            }
            Long originId = req.getRefundMedicineId();
            if (originId != null) {
                originMedReqIds.add(originId);
            }
        }
        Map<Long, MedicationRequest> originMedReqById = this.medicationRequestQueryService.mapEntitiesByIds(originMedReqIds);
        Map<Long, MedicationDispense> chargeAnchorDispenseByOriginReqId =
            this.loadChargeAnchorDispenseByOriginMedReqId(originMedReqIds);
        Set<Long> practitionerIds = new HashSet<>();
        for (MedicationRequest req : medReqById.values()) {
            this.collectRequesterId(practitionerIds, req);
        }
        for (MedicationRequest req : originMedReqById.values()) {
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
            MedicationRequest neg = medReqById.get(md.getMedReqId());
            Long refundOriginId = null;
            if (neg != null) {
                refundOriginId = neg.getRefundMedicineId();
            }
            if (refundOriginId == null) {
                refundOriginId = md.getMedReqId();
            }
            row.setRefundOriginRequestId(refundOriginId);
            MedicationRequest originReq = originMedReqById.get(refundOriginId);
            MedicationDispense chargeAnchor = chargeAnchorDispenseByOriginReqId.get(refundOriginId);
            if (chargeAnchor != null) {
                row.setChargeAnchorDispenseId(chargeAnchor.getId());
            }
            row.setItemId(md.getMedicationId());
            row.setItemName(this.resolveMedicationName(medicationById, md.getMedicationId()));
            row.setLotNumber(this.resolveLotNumber(md, chargeAnchor));
            row.setQuantity(this.absReturnMedicineQuantity(md.getDispenseQuantity(), md.getQuantity()));
            String unitCode = StringUtils.defaultString(md.getUnitCode());
            row.setUnitCode(unitCode);
            row.setUnitCodeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, unitCode));
            row.setTotalPrice(BigDecimal.ZERO);
            row.setDoctorName(this.resolveDoctorName(practitionerById, neg, originReq));
            DispenseStatus st = md.getStatusEnum();
            if (st != null) {
                row.setRefundEnum(st.getCode());
                row.setRefundEnumText(st.getDisplay());
            } else {
                row.setRefundEnum(GeneralSharedConstants.EMPTY_STRING);
                row.setRefundEnumText(GeneralSharedConstants.EMPTY_STRING);
            }
            if (neg != null) {
                row.setReqStatus(neg.getStatusEnum());
                RequestStatus rs = EnumUtil.fromCode(RequestStatus.class, neg.getStatusEnum());
                if (rs != null) {
                    row.setReqStatusText(rs.getDisplay());
                } else {
                    row.setReqStatusText(StringUtils.defaultString(neg.getStatusEnum()));
                }
            } else {
                row.setReqStatus(GeneralSharedConstants.EMPTY_STRING);
                row.setReqStatusText(GeneralSharedConstants.EMPTY_STRING);
            }
            row.setServiceTable(TableNameConstants.MED_MEDICATION_REQUEST);
            row.setTraceNo(this.resolveTraceNo(md, chargeAnchor));
            rows.add(row);
        }
        return rows;
    }

    /** 收集开立医生主键，供批量 Practitioner 查询。 */
    private void collectRequesterId(Set<Long> practitionerIds, MedicationRequest req) {
        if (req != null && req.getRequesterId() != null) {
            practitionerIds.add(req.getRequesterId());
        }
    }

    /**
     * 原正向药品医嘱 → 用于收费/批号/追溯码回显的首选发放单（主键升序第一条可计费发放）。
     */
    private Map<Long, MedicationDispense> loadChargeAnchorDispenseByOriginMedReqId(Set<Long> originMedReqIds) {
        Map<Long, MedicationDispense> anchorByOriginReqId = new HashMap<>();
        if (originMedReqIds == null || originMedReqIds.isEmpty()) {
            return anchorByOriginReqId;
        }
        List<MedicationDispense> dispenses =
            this.medicationDispenseQueryService.listMedicationDispensesByMedReqIds(originMedReqIds);
        Map<Long, Long> minAnchorIdByOriginReqId = new HashMap<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedReqId() == null || dispense.getId() == null) {
                continue;
            }
            if (!this.isChargeAnchorDispense(dispense)) {
                continue;
            }
            Long originReqId = dispense.getMedReqId();
            Long prevMin = minAnchorIdByOriginReqId.get(originReqId);
            if (prevMin == null || dispense.getId() < prevMin) {
                minAnchorIdByOriginReqId.put(originReqId, dispense.getId());
                anchorByOriginReqId.put(originReqId, dispense);
            }
        }
        return anchorByOriginReqId;
    }

    /**
     * 是否可作为批号/追溯码回显的「正向收费」发放（排除纯退药行，状态须已发/汇总/部分退等）。
     */
    private boolean isChargeAnchorDispense(MedicationDispense dispense) {
        if (MedicationDispenseType.REFUND.equals(dispense.getDispenseEnum())) {
            return false;
        }
        DispenseStatus st = dispense.getStatusEnum();
        if (st == null) {
            return false;
        }
        return DispenseStatus.COMPLETED.equals(st)
            || DispenseStatus.SUMMARIZED.equals(st)
            || DispenseStatus.PART_COMPLETED.equals(st)
            || DispenseStatus.PART_REFUND.equals(st)
            || DispenseStatus.REFUNDED.equals(st);
    }

    /** 批号：当前退药发放优先，否则回落原正向收费锚点发放。 */
    private String resolveLotNumber(MedicationDispense current, MedicationDispense chargeAnchor) {
        if (StringUtils.isNotBlank(current.getLotNumber())) {
            return current.getLotNumber().trim();
        }
        if (chargeAnchor != null && StringUtils.isNotBlank(chargeAnchor.getLotNumber())) {
            return chargeAnchor.getLotNumber().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 追溯码：规则同 {@link #resolveLotNumber}。 */
    private String resolveTraceNo(MedicationDispense current, MedicationDispense chargeAnchor) {
        if (StringUtils.isNotBlank(current.getTraceNo())) {
            return current.getTraceNo().trim();
        }
        if (chargeAnchor != null && StringUtils.isNotBlank(chargeAnchor.getTraceNo())) {
            return chargeAnchor.getTraceNo().trim();
        }
        return GeneralSharedConstants.EMPTY_STRING;
    }

    /** 开立医生展示名：退药医嘱 requester 优先，否则原正向医嘱。 */
    private String resolveDoctorName(
        Map<Long, Practitioner> practitionerById,
        MedicationRequest refundReq,
        MedicationRequest originReq) {
        Long requesterId = null;
        if (refundReq != null && refundReq.getRequesterId() != null) {
            requesterId = refundReq.getRequesterId();
        } else if (originReq != null) {
            requesterId = originReq.getRequesterId();
        }
        return this.resolvePractitionerName(practitionerById, requesterId);
    }

    /** 退药数量展示绝对值（发放数量优先于申请数量）。 */
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

    /** 批量加载医务人员（Map 索引）。 */
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

    /** 从发放行收集药品定义主键并批量加载。 */
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

    /** 药品名称回落（无定义时空串）。 */
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

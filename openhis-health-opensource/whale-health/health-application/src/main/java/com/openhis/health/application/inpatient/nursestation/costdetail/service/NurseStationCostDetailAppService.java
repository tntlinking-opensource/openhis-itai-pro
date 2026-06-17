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

package com.openhis.health.application.inpatient.nursestation.costdetail.service;

import com.openhis.health.module.common.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.inpatient.nursestation.costdetail.assembler.NurseStationCostDetailAssembler;
import com.openhis.health.module.querysupport.financial.dto.NurseStationCostDetailLineDto;
import com.openhis.health.module.querysupport.financial.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.mapper.NurseStationCostDetailMapper;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.LoadNurseStationCostDetailPageInitRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.QueryNurseStationInpatientCostDetailRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.LoadNurseStationCostDetailPageInitResponse;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.QueryNurseStationInpatientCostDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.module.querysupport.financial.InpatientNurseStationCostDetailLoader;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站费用明细应用编排。
 *
 * <p>页初始化：默认当日日期区间与明细/汇总展示选项。</p>
 * <p>费用查询：单就诊按日期区间拉取收费项，装配明细行或汇总行及净额合计。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NurseStationCostDetailAppService {

    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InpatientNurseStationCostDetailLoader inpatientNurseStationCostDetailLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final NurseStationCostDetailMapper nurseStationCostDetailMapper;
    private final NurseStationCostDetailAssembler nurseStationCostDetailAssembler;
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     *
     * @param request 可空；无业务字段
     */
    public LoadNurseStationCostDetailPageInitResponse loadNurseStationCostDetailPageInit(
        LoadNurseStationCostDetailPageInitRequest request) {
        // 服务端以本地日历日作为默认闭区间（BR-IP-CDT-002）
        LocalDate today = LocalDate.now();
        return nurseStationCostDetailMapper.buildPageInitResponse(today);
    }

    /**
     *
     * @return 明细行或汇总行 + 合计块
     * @throws BusinessException EX-IP-CDT-101～203
     */
    public QueryNurseStationInpatientCostDetailResponse queryNurseStationInpatientCostDetail(
        QueryNurseStationInpatientCostDetailRequest request) {
        try {
            this.validateQueryRequest(request);
            this.requireVisibleEncounter(request.getEncounterId(), request.getPatientId());
            ChargeItemQueryCommand chargeQuery = nurseStationCostDetailMapper.toChargeItemQueryCommand(request);
            List<InpatientNurseStationCostDetailChargeRow> chargeRows =
                this.inpatientNurseStationCostDetailLoader.listInpatientNurseStationCostDetailChargeRows(chargeQuery);
            Map<Long, ChargeItem> parentChargeById = this.loadParentChargeItemsById(chargeRows);
            List<ProductDefinitionBatchLookup> productLookups =
                nurseStationCostDetailMapper.toProductBatchLookups(chargeRows, parentChargeById);
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionBatchLoader.loadAsMap(productLookups);
            nurseStationCostDetailAssembler.validateChargeRowsForAssembly(
                chargeRows, productByKey, parentChargeById);
            NurseStationCostDetailTotalsDto totals =
                nurseStationCostDetailAssembler.buildTotals(chargeRows, productByKey, parentChargeById);
            List<NurseStationCostDetailLineDto> detailLines = new ArrayList<>();
            List<NurseStationCostSummaryLineDto> summaryLines = new ArrayList<>();
            String presentationMode = request.getPresentationMode().trim();
            if (InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
                detailLines = nurseStationCostDetailAssembler.buildDetailLines(
                    chargeRows, productByKey, parentChargeById);
            } else {
                summaryLines = nurseStationCostDetailAssembler.buildSummaryLines(
                    chargeRows, productByKey, parentChargeById);
            }
            return nurseStationCostDetailMapper.toQueryResponse(
                presentationMode, detailLines, summaryLines, totals);
        } catch (IllegalArgumentException illegalArgumentException) {
            log.warn("nurse station cost detail data incomplete: {}", illegalArgumentException.getMessage());
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_203,
                "费用数据不完整，请联系管理员核对收费项目配置");
        } catch (Exception exception) {
            log.error("nurse station cost detail query failed", exception);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_101,
                "费用明细加载失败，请稍后重试");
        }
    }

    /**
     * 查询请求必填与日期、展示模式合法（BR-IP-CDT-002/201）。
     *
     * @param request 查询请求
     */
    private void validateQueryRequest(QueryNurseStationInpatientCostDetailRequest request) {
        if (request == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_202,
                "请选择有效的查询日期");
        }
        if (request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_102,
                "当前患者不可用，请刷新左侧列表");
        }
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_202,
                "请选择有效的查询日期");
        }
        if (request.getDateFrom().isAfter(request.getDateTo())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_202,
                "请选择有效的查询日期");
        }
        if (StringUtils.isBlank(request.getPresentationMode())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_201,
                "查询参数错误，请刷新页面");
        }
        String mode = request.getPresentationMode().trim();
        if (!InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_DETAIL.equals(mode)
            && !InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_SUMMARY.equals(mode)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_201,
                "查询参数错误，请刷新页面");
        }
    }

    /**
     * 单就诊须在护士站可见范围且 patientId 与读模型一致（BR-IP-CDT-001，同 005 口径）。
     *
     * @param encounterId 目标就诊
     * @param patientId   卡片患者 id
     */
    private void requireVisibleEncounter(Long encounterId, Long patientId) {
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery query = nurseStationCostDetailMapper.toInpatientEncounterPageQuery(
            Collections.singletonList(encounterId), wardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            encounterContextFacade.listInpatientEncounterContexts(query);
        InpatientEncounterContextQueryModel encounterSnapshot = null;
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                encounterSnapshot = row;
                break;
            }
        }
        // 就诊不在护士站管辖队列内，拒绝查询以防横向越权
        if (encounterSnapshot == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_102,
                "当前患者不可用，请刷新左侧列表");
        }
        // 卡片 patientId 须与读模型一致（B.6.1 PatientEncounterConsistencyGate）
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounterSnapshot.getPatientId(),
            InpatientNurseStationConstants.EX_IP_CDT_102,
            "当前患者不可用，请刷新左侧列表");
    }

    /**
     *
     * @param chargeRows 域查询候选行
     * @return 父行 id → 实体
     */
    private Map<Long, ChargeItem> loadParentChargeItemsById(List<InpatientNurseStationCostDetailChargeRow> chargeRows) {
        Map<Long, ChargeItem> parentById = new HashMap<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return parentById;
        }
        Set<Long> parentIds = new LinkedHashSet<>();
        for (InpatientNurseStationCostDetailChargeRow row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            Long parentId = row.getChargeItem().getParentId();
            if (parentId != null) {
                parentIds.add(parentId);
            }
        }
        if (parentIds.isEmpty()) {
            return parentById;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(parentIds));
        List<ChargeItem> parents = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        for (ChargeItem parent : parents) {
            if (parent != null && parent.getId() != null) {
                parentById.put(parent.getId(), parent);
            }
        }
        return parentById;
    }

}

/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.service;

import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.inpatient.nursestation.costdetail.assembler.NurseStationCostDetailAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailLineDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.mapper.NurseStationCostDetailMapper;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.LoadNurseStationCostDetailPageInitRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.request.QueryNurseStationInpatientCostDetailRequest;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.LoadNurseStationCostDetailPageInitResponse;
import com.openhis.health.application.inpatient.nursestation.costdetail.response.QueryNurseStationInpatientCostDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.support.InpatientNurseStationCostDetailLoader;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
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
    private final EncounterContextReader EncounterContextReader;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InpatientNurseStationCostDetailLoader inpatientNurseStationCostDetailLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final NurseStationCostDetailMapper nurseStationCostDetailMapper;
    private final NurseStationCostDetailAssembler nurseStationCostDetailAssembler;
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final PractitionerQueryService practitionerQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 费用明细页初始化：默认当日日期区间与明细/汇总展示选项。
     *
     * @param request 可空；无业务字段
     * @return 页初始化响应（含默认日期与展示模式选项）
     */
    public LoadNurseStationCostDetailPageInitResponse loadNurseStationCostDetailPageInit(
        LoadNurseStationCostDetailPageInitRequest request) {
        // Gate：只读页初始化，不写库
        // Pre：以本地日历日作为默认闭区间（BR-IP-CDT-002）
        LocalDate today = LocalDate.now();
        // Asm：映射页初始化响应
        return nurseStationCostDetailMapper.buildPageInitResponse(today);
    }

    /**
     * 按就诊与日期区间查询住院费用明细或汇总。
     *
     * <p>可选 {@code keyword}：按展示产品名称、院内编码模糊过滤后再装配行与合计（BR-IP-CDT-019）。</p>
     * <p>可选 {@code excludeOffsetPairs=true}：费用清单打印成对剔除冲销留痕（原行+冲销行；
     * 冲销可在日期窗外仍剔除窗内原行）；页面 Tab 不传，留痕仍可见。</p>
     *
     * @param request 就诊、患者、日期区间、展示模式与可选项目关键字
     * @return 明细行或汇总行 + 合计块
     * @throws BusinessException EX-IP-CDT-101～203
     */
    public QueryNurseStationInpatientCostDetailResponse queryNurseStationInpatientCostDetail(
        QueryNurseStationInpatientCostDetailRequest request) {
        try {
            // Gate：查询参数、日期区间与展示模式合法
            this.validateQueryRequest(request);
            // Gate：就诊存在且 patientId 一致；已出院/病案场景不限护士站在科队列
            this.requireVisibleEncounter(request.getEncounterId(), request.getPatientId());
            // Pre：批量拉取收费行与父行、产品目录
            ChargeItemQueryCommand chargeQuery = nurseStationCostDetailMapper.toChargeItemQueryCommand(request);
            List<InpatientNurseStationCostDetailChargeRow> chargeRows =
                this.inpatientNurseStationCostDetailLoader.listInpatientNurseStationCostDetailChargeRows(chargeQuery);
            Map<Long, ChargeItem> parentChargeById = this.loadParentChargeItemsById(chargeRows);
            List<ProductDefinitionBatchLookup> productLookups =
                nurseStationCostDetailMapper.toProductBatchLookups(chargeRows, parentChargeById);
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
            nurseStationCostDetailAssembler.validateChargeRowsForAssembly(
                chargeRows, productByKey, parentChargeById);
            // Pre：排除嘱托账单（BR-IP-CDT-020）；再按关键字过滤（BR-IP-CDT-019）
            List<InpatientNurseStationCostDetailChargeRow> nonEntrustRows =
                nurseStationCostDetailAssembler.filterOutEntrustChargeRows(
                    chargeRows, productByKey, parentChargeById);
            List<InpatientNurseStationCostDetailChargeRow> displayRows =
                nurseStationCostDetailAssembler.filterChargeRowsByItemKeyword(
                    nonEntrustRows, productByKey, parentChargeById, request.getKeyword());
            // Pre：费用清单打印成对剔除冲销留痕（原行+冲销行；冲销可在日期窗外）
            if (Boolean.TRUE.equals(request.getExcludeOffsetPairs())) {
                displayRows = this.filterDisplayRowsForOffsetPairs(displayRows);
            }
            NurseStationCostDetailTotalsDto totals =
                nurseStationCostDetailAssembler.buildTotals(displayRows, productByKey, parentChargeById);
            // Pre：识别床位自动计费 SR（auto-roll-fees），供明细来源/汇总操作类型覆盖展示
            Set<Long> autoRollServiceRequestIds = this.resolveAutoRollServiceRequestIds(displayRows);
            List<NurseStationCostDetailLineDto> detailLines = new ArrayList<>();
            List<NurseStationCostSummaryLineDto> summaryLines = new ArrayList<>();
            String presentationMode = request.getPresentationMode().trim();
            // Branch：按展示模式装配明细行或汇总行
            if (InpatientNurseStationConstants.COST_DETAIL_PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
                detailLines = nurseStationCostDetailAssembler.buildDetailLines(
                    displayRows, productByKey, parentChargeById, autoRollServiceRequestIds);
            } else {
                // Pre：批量加载汇总展开明细涉及的操作人展示名
                Map<Long, String> practitionerNameById =
                    this.loadPractitionerNamesForChargeRows(displayRows);
                summaryLines = nurseStationCostDetailAssembler.buildSummaryLines(
                    displayRows, productByKey, parentChargeById, practitionerNameById, autoRollServiceRequestIds);
            }
            // Asm：映射查询响应
            return nurseStationCostDetailMapper.toQueryResponse(
                presentationMode, detailLines, summaryLines, totals);
        } catch (BusinessException businessException) {
            // 业务门禁/校验原文上抛，禁止吞成泛化「加载失败」
            throw businessException;
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
        if (StringUtils.isEmpty(request.getPresentationMode())) {
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
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_201,
                "查询参数错误，请刷新页面");
        }
        String statusEnum = request.getStatusEnum().trim();
        if (!InpatientNurseStationConstants.COST_DETAIL_STATUS_FILTER_PENDING_SETTLEMENT.equals(statusEnum)
            && !InpatientNurseStationConstants.COST_DETAIL_STATUS_FILTER_SETTLED.equals(statusEnum)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_CDT_201,
                "查询参数错误，请刷新页面");
        }
    }

    /**
     * 校验就诊可查费用：优先护士站在科可见；若已出院/病案等不在在科队列，则按就诊主键回落读取。
     *
     * <p>patientId 须与住院读模型一致（BR-IP-CDT-001）。病案首页费用清单依赖回落分支。</p>
     *
     * @param encounterId 目标就诊
     * @param patientId   请求患者 id
     */
    private void requireVisibleEncounter(Long encounterId, Long patientId) {
        List<Long> wardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery query = nurseStationCostDetailMapper.toInpatientEncounterPageQuery(
            Collections.singletonList(encounterId), wardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        InpatientEncounterContextQueryModel encounterSnapshot = null;
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && encounterId.equals(row.getEncounterId())) {
                encounterSnapshot = row;
                break;
            }
        }
        // 不在护士站在科队列：按就诊主键回落（已出院/已结算/病案场景）
        if (encounterSnapshot == null) {
            encounterSnapshot =
                EncounterContextReader.getInpatientContextByEncounterId(encounterId).orElse(null);
        }
        // 就诊不存在或不可读，拒绝查询
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
     * 费用清单打印：按 refund_id 成对剔除冲销留痕（含窗外冲销反查原行）。
     *
     * @param displayRows 关键字过滤后的候选行
     * @return 剔除成对冲销后的行
     */
    private List<InpatientNurseStationCostDetailChargeRow> filterDisplayRowsForOffsetPairs(
        List<InpatientNurseStationCostDetailChargeRow> displayRows) {
        if (displayRows == null || displayRows.isEmpty()) {
            return List.of();
        }
        // Pre：收集窗内收费主键，反查任意冲销行（可跨日期）指向这些原行
        Set<Long> chargeIdsInWindow = new LinkedHashSet<>();
        for (InpatientNurseStationCostDetailChargeRow row : displayRows) {
            if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
                continue;
            }
            chargeIdsInWindow.add(row.getChargeItem().getId());
        }
        Set<Long> offsetSourceIdsOutsideWin = new LinkedHashSet<>();
        if (!chargeIdsInWindow.isEmpty()) {
            List<ChargeItem> offsetRows =
                chargeItemQueryService.listChargeItemsByRefundIds(chargeIdsInWindow);
            for (ChargeItem offsetRow : offsetRows) {
                if (offsetRow == null || offsetRow.getRefundId() == null) {
                    continue;
                }
                offsetSourceIdsOutsideWin.add(offsetRow.getRefundId());
            }
        }
        return nurseStationCostDetailAssembler.filterOutOffsetPairedChargeRows(
            displayRows, offsetSourceIdsOutsideWin);
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

    /**
     * 批量加载费用候选行涉及操作人的展示名（汇总展开明细用）。
     *
     * @param chargeRows 域查询候选行
     * @return 操作人 id → 姓名；无命中时空 Map
     */
    private Map<Long, String> loadPractitionerNamesForChargeRows(
        List<InpatientNurseStationCostDetailChargeRow> chargeRows) {
        Map<Long, String> practitionerNameById = new HashMap<>();
        Set<Long> operatorIds = nurseStationCostDetailAssembler.collectOperatorIdsForChargeRows(chargeRows);
        if (operatorIds.isEmpty()) {
            return practitionerNameById;
        }
        List<Practitioner> practitioners = practitionerQueryService.findAllByIds(operatorIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null
                && StringUtils.isNotEmpty(practitioner.getName())) {
                practitionerNameById.put(practitioner.getId(), practitioner.getName().trim());
            }
        }
        return practitionerNameById;
    }

    /**
     * 从费用候选行解析床位自动计费 SR 主键（generate_source=auto-roll-fees）。
     *
     * @param chargeRows 域查询候选行
     * @return auto-roll-fees 诊疗申请主键集合
     */
    private Set<Long> resolveAutoRollServiceRequestIds(List<InpatientNurseStationCostDetailChargeRow> chargeRows) {
        Set<Long> serviceRequestIds = new LinkedHashSet<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return serviceRequestIds;
        }
        String orderExecute = GenerateSource.ORDER_EXECUTE.getCode();
        String serviceTable = TableNameConstants.WKF_SERVICE_REQUEST;
        for (InpatientNurseStationCostDetailChargeRow row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            // 仅 order-execute + 诊疗申请锚点才可能是床位滚费
            if (!orderExecute.equals(charge.getGenerateSourceEnum())) {
                continue;
            }
            if (charge.getServiceId() == null || StringUtils.isEmpty(charge.getServiceTable())) {
                continue;
            }
            if (!serviceTable.equalsIgnoreCase(charge.getServiceTable().trim())) {
                continue;
            }
            serviceRequestIds.add(charge.getServiceId());
        }
        if (serviceRequestIds.isEmpty()) {
            return serviceRequestIds;
        }
        Map<Long, ServiceRequestListModel> modelById =
            serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Set<Long> autoRollIds = new LinkedHashSet<>();
        String autoRollSource = GenerateSource.AUTO_ROLL_FEES.getCode();
        for (Map.Entry<Long, ServiceRequestListModel> entry : modelById.entrySet()) {
            ServiceRequestListModel model = entry.getValue();
            if (model == null) {
                continue;
            }
            // 床位配置 ensure 的长期 SR 带 auto-roll-fees
            if (autoRollSource.equals(model.getGenerateSourceEnum())) {
                autoRollIds.add(entry.getKey());
            }
        }
        return autoRollIds;
    }

}

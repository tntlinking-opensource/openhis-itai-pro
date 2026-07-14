/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.encountermanage.service;

import com.openhis.health.application.outpatient.doctorstation.encountermanage.dto.OutpatientInfoItemDto;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.mapper.DoctorStationEncounterManageMapper;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.CompleteOutpatientEncounterRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.MarkOutpatientLeaveRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.PageOutpatientInfoForDoctorStationRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.StartOutpatientReceptionRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.CompleteOutpatientEncounterResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.MarkOutpatientLeaveResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.PageOutpatientInfoForDoctorStationResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.StartOutpatientReceptionResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.CompleteOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.MarkOutpatientLeaveCommand;
import com.openhis.health.domain.administration.encounter.command.StartOutpatientReceptionCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterCompletionCheckQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterListFinancialTotals;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryEncounterContextExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryEncounterContextQueryExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryEncounterContextExtensionOut;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊医生站就诊管理编排：患者队列、接诊/暂离/完诊与费用性质下拉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationEncounterManageAppService {

    /** 就诊读侧：门诊上下文分页、完诊预检与 mustGet。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    /** 就诊写侧：接诊、暂离、完诊命令编排。 */
    private final EncounterDomainService encounterDomainService;
    /** 字段映射：Request/QueryModel ↔ 医生站列表与命令 DTO。 */
    private final DoctorStationEncounterManageMapper doctorStationEncounterManageMapper;
    /** 医务人员读侧：接诊医师展示名解析。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 收费明细读侧：列表页按就诊批量汇总费用。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 工伤就诊上下文（队列角标）。 */
    private final WorkInjuryEncounterContextExtension workInjuryEncounterContextExtension;

    /**
     * 分页查询本科室门诊患者列表（读模型）。
     *
     * <p>{@code encounterStatus} 未传时：默认状态为已挂号、在诊、暂离、诊毕并集；
     * 须 {@code staffPractitionerId}，读侧按 BR-DOR-009 做状态×参与者可见性过滤。</p>
     *
     * @param request 列表查询条件
     * @return 分页结果（{@code feeNatureOptions} 为空，医嘱开立 init 按就诊加载）
     * @throws BusinessException 参数非法时（分页/在诊队列缺医师键等）
     */
    public PageOutpatientInfoForDoctorStationResponse pageOutpatientInfoForDoctorStation(
        PageOutpatientInfoForDoctorStationRequest request) {
        // 门控：医生站列表查询须带齐队列语义与分页，否则患者队列无法唯一定义。
        this.validatePageOutpatientInfoForDoctorStationRequest(request);
        // 将应用请求映射为读模型分页查询入参（含在诊态下的接诊医师过滤键）。
        OutpatientEncounterPageQuery pageQuery =
            this.doctorStationEncounterManageMapper.toOutpatientEncounterPageQuery(request);
        // 下推到就诊上下文分页查询，避免应用层全表加载。
        Page<OutpatientEncounterContextQueryModel> page =
            this.EncounterContextReader.pageOutpatientEncounterContexts(pageQuery);
        // 读模型未投影金额；按就诊一次批量拉收费明细，内存汇总（不查支付流水、不按号源锚点拆挂号费）。
        Map<Long, OutpatientEncounterListFinancialTotals> chargeTotals =
            this.buildChargeItemTotalsForDoctorStationPage(page.getContent());
        // 将读模型分页装配为前端列表契约（无写库）。
        PageOutpatientInfoForDoctorStationResponse resp =
            this.doctorStationEncounterManageMapper.toPageOutpatientInfoForDoctorStationResponse(page, chargeTotals);
        this.applyWorkInjuryContextLabels(resp);
        // 费用性质下拉随就诊挂号分类变化，患者列表页不附带（由医嘱 init 按 encounterId 加载）。
        resp.setFeeNatureOptions(Collections.emptyList());
        return resp;
    }

    /**
     * 门诊医生站列表：按就诊解析工伤上下文并回填 workInjury 相关展示列。
     *
     * @param resp 分页响应
     */
    private void applyWorkInjuryContextLabels(PageOutpatientInfoForDoctorStationResponse resp) {
        if (resp == null || resp.getContent() == null || resp.getContent().isEmpty()) {
            return;
        }
        for (OutpatientInfoItemDto row : resp.getContent()) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            WorkInjuryEncounterContextExtensionOut ctx = workInjuryEncounterContextExtension.resolveContext(
                    new WorkInjuryEncounterContextQueryExtensionIn().setEncounterId(row.getEncounterId()));
            if (ctx == null) {
                continue;
            }
            row.setWorkInjuryEncounter(ctx.isWorkInjuryEncounter());
            row.setWorkInjuryApprovalStatusLabel(ctx.getApprovalStatusLabel());
            row.setWorkInjuryContractId(ctx.getContractId());
            row.setWorkInjuryQualificationValid(ctx.isQualificationValid());
            row.setWorkInjuryNeeds2301Upload(ctx.isNeeds2301Upload());
        }
    }

    /**
     * 医生接诊（门诊），写入接诊时刻并切换接诊参与者。
     *
     * @param request 接诊请求
     * @return 接诊受理结果
     * @throws BusinessException 就诊不存在、类别非门诊、状态不允许接诊或并发冲突
     */
    public StartOutpatientReceptionResponse startOutpatientReception(StartOutpatientReceptionRequest request) {
        // 门控：接诊必须指向明确就诊、医师与操作人，否则无法记账到具体责任主体。
        this.validateStartOutpatientReceptionRequest(request);
        // 强一致加载就诊头表，用于应用层门诊类别门控（避免对非门诊行误触发接诊）。
        EncounterDetailQueryModel encounterHead = this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        // 医生站接诊仅针对门诊类别就诊。
        if (!EncounterClass.AMB.getCode().equals(encounterHead.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此工作站执行门诊接诊");
        }
        // 解析接诊医师展示名，写入领域命令并由事件携带，投影器与事件字段名一致（doctorId/doctorName）。
        // 医师不存在时阻断接诊，避免无效医师标识进入领域
        Practitioner attendingDoctor = this.practitionerQueryService.findById(request.getDoctorId());
        if (attendingDoctor == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_NOT_FOUND, "接诊医师不存在或已失效");
        }
        String doctorDisplayName = attendingDoctor.getName();
        StartOutpatientReceptionCommand command =
            this.doctorStationEncounterManageMapper.toStartOutpatientReceptionCommand(request, doctorDisplayName);
        this.encounterDomainService.startOutpatientReception(command);
        // 审计：记录操作者与就诊，便于追踪接诊责任人与时间线。
        log.info("门诊接诊完成 encounterId={} doctorId={} operatorId={}",
            request.getEncounterId(), request.getDoctorId(), request.getOperatorId());
        // 返回受理结果给医生站刷新队列。
        return this.doctorStationEncounterManageMapper.toStartOutpatientReceptionResponse(request.getEncounterId(), true);
    }

    /**
     * 患者暂离，将就诊置为暂停态并记录原因。
     *
     * @param request 暂离请求
     * @return 暂离结果
     * @throws BusinessException 就诊不存在或当前状态不允许暂离
     */
    public MarkOutpatientLeaveResponse markOutpatientLeave(MarkOutpatientLeaveRequest request) {
        // 门控：暂离必须绑定就诊与操作人，避免匿名操作改动候诊秩序。
        this.validateMarkOutpatientLeaveRequest(request);
        // 确认就诊主档存在，缺失时禁止进入写命令以防写幽灵就诊。
        this.encounterQueryService.mustGetEncounter(request.getEncounterId());
        MarkOutpatientLeaveCommand command = this.doctorStationEncounterManageMapper.toMarkOutpatientLeaveCommand(request);
        this.encounterDomainService.markOutpatientLeave(command);
        log.info("门诊暂离完成 encounterId={} operatorId={}", request.getEncounterId(), request.getOperatorId());
        return this.doctorStationEncounterManageMapper.toMarkOutpatientLeaveResponse(request.getEncounterId(), true);
    }

    /**
     * 门诊完诊，结束就诊并触发诊毕事件。
     *
     * @param request 完诊请求
     * @return 完诊结果
     * @throws BusinessException 预检不通过、缺少主诊断/负责医师（Cmd 内断言）或状态非法
     */
    public CompleteOutpatientEncounterResponse completeOutpatientEncounter(CompleteOutpatientEncounterRequest request) {
        // 门控：完诊必须指向有效就诊与操作人，防止空主键误完诊。
        this.validateCompleteOutpatientEncounterRequest(request);
        // 读侧完诊资格预检（类别/状态/活动接诊医生），不通过则不得进入写命令。
        EncounterCompletionCheckQueryModel check = this.encounterQueryService.checkOutpatientCompletable(request.getEncounterId());
        // 完诊资格检查不通过时，使用检查返回的错误码与消息阻断流程
        if (!Boolean.TRUE.equals(check.getCompletable())) {
            String code = check.getFailureCode();
            if (code == null || code.isEmpty()) {
                code = ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT;
            }
            String message = check.getFailureMessage();
            if (message == null || message.isEmpty()) {
                message = "当前不满足门诊完诊条件";
            }
            throw new BusinessException(code, message);
        }
        CompleteOutpatientEncounterCommand command =
            this.doctorStationEncounterManageMapper.toCompleteOutpatientEncounterCommand(request);
        this.encounterDomainService.completeOutpatientEncounter(command);
        log.info("门诊完诊完成 encounterId={} operatorId={}", request.getEncounterId(), request.getOperatorId());
        return this.doctorStationEncounterManageMapper.toCompleteOutpatientEncounterResponse(request.getEncounterId(), true);
    }

    /**
     * 校验医生站列表查询入参。
     *
     * @param request 请求体
     * @throws BusinessException 参数缺失或在诊列表缺少医师过滤键
     */
    private void validatePageOutpatientInfoForDoctorStationRequest(PageOutpatientInfoForDoctorStationRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "页码须从 1 开始");
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "页大小须为正整数");
        }
        // 医生站列表须携带当前登录医师，读侧才能按 BR-DOR-009 判定挂号医生/接诊医生可见范围。
        if (request.getStaffPractitionerId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "须提供当前登录医师主键以按可见性规则过滤患者列表");
        }
    }

    /**
     * 校验接诊请求。
     *
     * @param request 请求体
     * @throws BusinessException 参数缺失
     */
    private void validateStartOutpatientReceptionRequest(StartOutpatientReceptionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (request.getDoctorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "接诊医师不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "操作人不能为空");
        }
    }

    /**
     * 校验暂离请求。
     *
     * @param request 请求体
     * @throws BusinessException 参数缺失
     */
    private void validateMarkOutpatientLeaveRequest(MarkOutpatientLeaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "操作人不能为空");
        }
    }

    /**
     * 校验完诊请求。
     *
     * @param request 请求体
     * @throws BusinessException 参数缺失
     */
    private void validateCompleteOutpatientEncounterRequest(CompleteOutpatientEncounterRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "操作人不能为空");
        }
    }

    /**
     * 为本页门诊读模型行构建「就诊主键 → 收费汇总」映射。
     * <p>数据库 I/O 仅一次 {@link ChargeItemQueryService#listChargeItemsByEncounterIds}（A.9.1 批量）；
     * 与挂号列表按就诊批量拉明细后内存汇总的模式一致，区别为本方法不按号源锚点过滤，
     * 该次就诊下全部非删明细计入总额，并按 {@link ChargeItemStatus} 将已结算/已退款金额分列。</p>
     *
     * @param outpatientContextRows 当前页 {@link OutpatientEncounterContextQueryModel} 列表，允许为空
     * @return 本页出现的每个就诊主键对应一条汇总；无就诊 ID 时返回空 Map；有就诊时先置零再累加，保证列表行总能取到占位
     */
    private Map<Long, OutpatientEncounterListFinancialTotals> buildChargeItemTotalsForDoctorStationPage(
        List<OutpatientEncounterContextQueryModel> outpatientContextRows) {
        Map<Long, OutpatientEncounterListFinancialTotals> totalsByEncounter = new HashMap<>();
        // 空页不访问收费库，避免无意义批量查询。
        if (outpatientContextRows == null || outpatientContextRows.isEmpty()) {
            return totalsByEncounter;
        }
        // 收集本页就诊主键并去重，保持与挂号列表批量策略一致。
        LinkedHashSet<Long> encounterIdsOnPage = new LinkedHashSet<>();
        for (OutpatientEncounterContextQueryModel infoRow : outpatientContextRows) {
            if (infoRow.getEncounterId() != null) {
                encounterIdsOnPage.add(infoRow.getEncounterId());
            }
        }
        // 本页无有效就诊锚点时直接返回，避免构造空 IN 条件。
        if (encounterIdsOnPage.isEmpty()) {
            return totalsByEncounter;
        }
        // 先为每个就诊写入零基线，前端可区分「无明细」与「未汇总」；后续仅做加法。
        for (Long encounterId : encounterIdsOnPage) {
            OutpatientEncounterListFinancialTotals emptyTotals = new OutpatientEncounterListFinancialTotals();
            emptyTotals.setTotalAmount(BigDecimal.ZERO);
            emptyTotals.setPaidAmount(BigDecimal.ZERO);
            emptyTotals.setRefundAmount(BigDecimal.ZERO);
            totalsByEncounter.put(encounterId, emptyTotals);
        }
        ChargeItemQueryCommand batchCmd = new ChargeItemQueryCommand();
        batchCmd.setEncounterIds(new ArrayList<>(encounterIdsOnPage));
        List<ChargeItem> chargeLines = this.chargeItemQueryService.listChargeItemsByEncounterIds(batchCmd);
        // 持久化层以字符串存状态码，与枚举 getCode() 对齐做分支，避免魔法字面量散落在实现类字段上（B.4）。
        String settledCode = ChargeItemStatus.SETTLED.getCode();
        String refundedCode = ChargeItemStatus.REFUNDED.getCode();
        for (ChargeItem chargeLine : chargeLines) {
            // 跳过空行或已逻辑删除明细：软删行不参与患者费用展示。
            if (chargeLine == null || Boolean.TRUE.equals(chargeLine.getIsDeleted())) {
                continue;
            }
            Long encounterId = chargeLine.getEncounterId();
            BigDecimal lineTotalPrice = chargeLine.getTotalPrice();
            // 无就诊锚点或无金额的明细无法归属汇总，丢弃以免污染 totals。
            if (encounterId == null || lineTotalPrice == null) {
                continue;
            }
            OutpatientEncounterListFinancialTotals bucket = totalsByEncounter.get(encounterId);
            // 防御：批量结果若含本页外的就诊（异常数据），跳过。
            if (bucket == null) {
                continue;
            }
            // 全部有效明细计入费用总额（应收面）。
            bucket.setTotalAmount(bucket.getTotalAmount().add(lineTotalPrice));
            String persistedStatus = chargeLine.getStatusEnum();
            // 已结算：计入「实收/已结」展示列。
            if (settledCode.equals(persistedStatus)) {
                bucket.setPaidAmount(bucket.getPaidAmount().add(lineTotalPrice));
            } else if (refundedCode.equals(persistedStatus)) {
                // 已退款：计入退费展示列（与支付流水无关，仅跟明细终态）。
                bucket.setRefundAmount(bucket.getRefundAmount().add(lineTotalPrice));
            }
        }
        return totalsByEncounter;
    }
}

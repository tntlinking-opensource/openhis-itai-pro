/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.service;

import com.openhis.health.application.inpatient.nursestation.orderexecute.response.ExecuteNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderexecute.service.NurseStationOrderExecuteAppService;
import com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.dto.SurgeryAnesthExecuteDialogDto;
import com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.mapper.InpatientNurseStationSurgeryAnesthExecuteMapper;
import com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.request.LoadSurgeryAnesthExecuteDialogRequest;
import com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.request.SubmitSurgeryAnesthExecuteRequest;
import com.openhis.health.application.inpatient.nursestation.surgeryanesthexecute.response.SubmitSurgeryAnesthExecuteResponse;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.inventory.InventoryListPractitionerNameResolver;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 住院护士站手术与麻醉执行弹窗应用服务（AS-IP-NUR-016）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IP-SAE-01</b>：装载弹窗——就诊可见性 + SR 横表回填 + 类目 Gate（24/25）</li>
 *   <li><b>IF-IP-SAE-02</b>：提交弹窗——操作人 Gate + 麻醉起止校验 + 委托 {@link NurseStationOrderExecuteAppService#executeNurseStationOrders}
 *       （EXE-02 Pre/Gate/计费；弹窗横表字段与多角色 performer 由写槽位标记驱动）</li>
 *   <li>{@link CurrentPractitionerResolver}：写路径操作人门控（B.6.1）</li>
 *   <li>{@link EncounterContextReader}：病区可见性 Gate</li>
 *   <li>{@link ServiceRequestQueryService}：读申请横表（只读）</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientNurseStationSurgeryAnesthExecute/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientNurseStationSurgeryAnesthExecuteAppService {

    /** 写路径操作人门控（B.6.1；SAE-02 在委托 EXE 前先决）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 护士站管辖病区收窄（对齐 EXE 可见性）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 住院就诊上下文可见性。 */
    private final EncounterContextReader EncounterContextReader;

    /** 服务申请读侧：弹窗回填横表。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 诊疗活动定义读侧：项目名回显。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 医务人员姓名批量解析。 */
    private final InventoryListPractitionerNameResolver practitionerNameResolver;

    /** 字段映射：Request/QueryModel → DTO/EXE 写槽。 */
    private final InpatientNurseStationSurgeryAnesthExecuteMapper surgeryAnesthExecuteMapper;

    /** 医嘱执行主路径（IF-IP-EXE-02）；SAE-02 委托计费/发药编排。 */
    private final NurseStationOrderExecuteAppService nurseStationOrderExecuteAppService;

    /**
     * IF-IP-SAE-01：装载手术/麻醉执行弹窗（不写库）。
     *
     * @param request 就诊、SR、计划槽位
     * @return 弹窗回填字段（只读医师 + 可编护士/时间/样本备注）
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_SAE_201} 非 24/25 类目；
     *                           就诊不可见时透传 EXE 可见性异常
     */
    public SurgeryAnesthExecuteDialogDto loadSurgeryAnesthExecuteDialog(LoadSurgeryAnesthExecuteDialogRequest request) {
        // Gate：空请求拒绝
        if (request == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "请求不能为空");
        }
        // Gate：就诊须在护士站管辖病区可见（对齐 EXE）
        this.assertEncounterVisible(request.getEncounterId());
        // Pre：加载 SR 横表（含拟手术人员/手术时间等）
        ServiceRequestListModel sr = serviceRequestQueryService.getListModelById(request.getServiceRequestId());
        // Gate：申请不存在则无法装载弹窗
        if (sr == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "服务申请不存在");
        }
        // Gate：SR 须归属本就诊，防止跨就诊打开弹窗
        if (request.getEncounterId() != null && !request.getEncounterId().equals(sr.getEncounterId())) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "服务申请与就诊不匹配");
        }
        // Gate：仅手术 24 / 麻醉 25 打开本弹窗（BR-IP-SAE-001）
        this.assertSurgeryOrAnesthesiaCategory(sr.getCategoryEnum());
        // Pre：批量解析人员展示名 + 项目名
        Map<Long, String> nameById = practitionerNameResolver.resolveNamesByIds(this.collectPerformerIds(sr));
        String activityName = this.resolveActivityName(sr.getActivityId());
        // Asm：组装弹窗 DTO（只读医师；护士/手术时间可编默认值）
        return surgeryAnesthExecuteMapper.toDialogDto(sr, request.getPlanTime(), activityName, nameById);
    }

    /**
     * IF-IP-SAE-02：提交手术/麻醉执行弹窗并挂接 EXE-02 计费主路径。
     *
     * <p>写顺序由 EXE 内 create → performer → complete → Charge/Dispense 完成；
     * 本方法不写 {@code mrms_homepage_procedure}（BR-IP-SAE-010）。</p>
     *
     * @param request 弹窗字段 + EXE 槽位/账户/绑定项
     * @return 处理结果（透传 EXE processedCount/warnings）
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_SAE_201} 类目非法；
     *                           {@link InpatientNurseStationConstants#EX_IP_SAE_203} 麻醉起止非法；
     *                           其余库存/槽位异常透传 EXE
     */
    public SubmitSurgeryAnesthExecuteResponse submitSurgeryAnesthExecute(SubmitSurgeryAnesthExecuteRequest request) {
        // Gate：空请求拒绝
        if (request == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "请求不能为空");
        }
        // Gate：写路径须已登录医务人员（B.6.1；EXE 内仍会再取 operatorId）
        currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_EXE_303,
            "未登录或登录已失效，无法执行手术/麻醉");
        // Gate：就诊可见性
        this.assertEncounterVisible(request.getEncounterId());
        // Pre：复读 SR 做类目 Gate（写前不以 QueryService 当状态真源，仅类目过滤）
        ServiceRequestListModel sr = serviceRequestQueryService.getListModelById(request.getServiceRequestId());
        // Gate：申请不存在则无法执行
        if (sr == null) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "服务申请不存在");
        }
        // Gate：禁止跨就诊提交
        if (request.getEncounterId() != null && !request.getEncounterId().equals(sr.getEncounterId())) {
            throw new BusinessException(InpatientNurseStationConstants.EX_IP_EXE_303, "服务申请与就诊不匹配");
        }
        // Gate：仅 24/25（BR-IP-SAE-001）
        this.assertSurgeryOrAnesthesiaCategory(sr.getCategoryEnum());
        // Pre：麻醉结束不得早于开始（EX-IP-SAE-203）
        this.assertAnesthesiaTimeRange(request.getAnesthesiaStartTime(), request.getAnesthesiaEndTime());
        // Cmd：委托 IF-IP-EXE-02——弹窗横表与多角色 performer 由写槽位 surgeryAnesthExecuteFlag 驱动
        ExecuteNurseStationOrdersResponse executeResponse =
            nurseStationOrderExecuteAppService.executeNurseStationOrders(
                surgeryAnesthExecuteMapper.toExecuteOrdersRequest(request));
        // Asm：透传 EXE 处理计数与软提示
        return surgeryAnesthExecuteMapper.toSubmitResponse(executeResponse);
    }

    /**
     * 校验护士站就诊可见性（管辖病区）。
     *
     * @param encounterId 就诊主键
     * @throws BusinessException 就诊不在管辖病区可见范围时
     */
    private void assertEncounterVisible(Long encounterId) {
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        EncounterContextReader.assertEncounterVisible(
            encounterId,
            managedWardIds,
            InpatientNurseStationConstants.EX_IP_EXE_303,
            "请先选择患者或无权访问");
    }

    /**
     * 断言类目为手术 24 或麻醉 25（BR-IP-SAE-001）。
     *
     * @param categoryEnum SR.category_enum
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_SAE_201} 非 24/25
     */
    private void assertSurgeryOrAnesthesiaCategory(String categoryEnum) {
        boolean surgery = ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(categoryEnum);
        boolean anesthesia = ServiceRequestCategoryConstants.SERVICE_CATEGORY_ANESTHESIA.equals(categoryEnum);
        // 非手术/麻醉类目应走普通 EXE-02，拒绝本弹窗接口
        if (!surgery && !anesthesia) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SAE_201,
                "非手术/麻醉医嘱请走普通执行");
        }
    }

    /**
     * 麻醉起止合法性：两端皆填时结束不得早于开始。
     *
     * @param start 开始
     * @param end   结束
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_SAE_203} 结束早于开始
     */
    private void assertAnesthesiaTimeRange(
        java.time.LocalDateTime start,
        java.time.LocalDateTime end) {
        // 起止任一未填则跳过区间校验（允许只登一侧）
        if (start == null || end == null) {
            return;
        }
        // 结束早于开始则拒绝（EX-IP-SAE-203）
        if (end.isBefore(start)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SAE_203,
                "麻醉结束时间不能早于开始时间");
        }
    }

    /**
     * 收集弹窗需回显姓名的医务人员主键。
     *
     * @param sr 服务申请
     * @return 主键列表（可含 null，解析器会忽略）
     */
    private List<Long> collectPerformerIds(ServiceRequestListModel sr) {
        List<Long> ids = new ArrayList<>();
        // SR 缺失时返回空列表，避免 NPE
        if (sr == null) {
            return ids;
        }
        ids.add(sr.getSurgeonId());
        ids.add(sr.getFirstAssistantId());
        ids.add(sr.getSecondAssistantId());
        ids.add(sr.getAnesthetistId());
        ids.add(sr.getNurseId());
        return ids;
    }

    /**
     * 解析诊疗项目展示名。
     *
     * @param activityId ActivityDefinition 主键
     * @return 名称；无则 null
     */
    private String resolveActivityName(Long activityId) {
        // 无活动定义主键则无项目名可回显
        if (activityId == null) {
            return null;
        }
        Optional<ActivityDefinition> optional =
            activityDefinitionQueryService.findOptionalActivityDefinitionEntityById(activityId);
        // 目录行已删或未同步时弹窗仍可打开，项目名置空
        if (optional.isEmpty()) {
            return null;
        }
        ActivityDefinition definition = optional.get();
        return definition.getName();
    }
}

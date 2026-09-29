/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestBusinessTimeKind;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务申请聚合根（非药品医嘱）。
 *
 * <p>对应表：wkf_service_request。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_service_request")
public class ServiceRequest extends BaseAuditedEntity {

    @Column(name = "bus_no")
    private String busNo;

    @Column(name = "prescription_no")
    private String prescriptionNo;

    @Column(name = "based_on_table")
    private String basedOnTable;

    @Column(name = "based_on_id")
    private Long basedOnId;

    @Column(name = "requisition_id")
    private Long requisitionId;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "intent_enum")
    private String intentEnum;

    @Column(name = "category_enum", nullable = false)
    private String categoryEnum;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "priority_enum")
    private String priorityEnum;

    @Column(name = "perform_flag")
    private Boolean performFlag;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    /**
     * 计划执行次数（临嘱/门诊：护士站拆解可执行上限；长嘱按频次执行时可为空）。
     */
    @Column(name = "execution_count")
    private Integer executionCount;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /**
     * 开立时间：首次建草稿/直建落库写一次；签发、改草稿、签退不覆盖。
     */
    @Column(name = "req_opened_time")
    private LocalDateTime reqOpenedTime;

    /**
     * 签发时间：仅签发（及无草稿直建）写入；列名与药/耗材 {@code req_authored_time} 对齐。
     */
    @Column(name = "req_authored_time")
    private LocalDateTime reqAuthoredTime;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "performer_type_code")
    private String performerTypeCode;

    @Column(name = "performer_id")
    private Long performerId;

    @Column(name = "performer_check_id")
    private Long performerCheckId;

    @Column(name = "check_time")
    private LocalDateTime checkTime;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "reason_text")
    private String reasonText;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "occurrence_start_time")
    private LocalDateTime occurrenceStartTime;

    /**
     * 计划结束时间（临床计划窗；停嘱不再写入本列）。
     */
    @Column(name = "occurrence_end_time")
    private LocalDateTime occurrenceEndTime;

    /**
     * 停嘱时间：长期停嘱写入；取消停嘱清空；执行 Gate / 频次右界读本列；改停嘱时间可覆盖（不改停嘱人）。
     */
    @Column(name = "stop_time")
    private LocalDateTime stopTime;

    /**
     * 停嘱人：停嘱操作写入；取消停嘱清空；改停嘱时间不改本列。
     */
    @Column(name = "stopper_id")
    private Long stopperId;

    /**
     * 停嘱校对人：护士确认医生停嘱后写入；取消停嘱校对或取消停嘱时清空；不改状态。
     */
    @Column(name = "stop_performer_check_id")
    private Long stopPerformerCheckId;

    /**
     * 停嘱校对时间：与 {@link #stopPerformerCheckId} 成对；改停嘱时间不改本列。
     */
    @Column(name = "stop_check_time")
    private LocalDateTime stopCheckTime;

    @Column(name = "relevant_history_id")
    private Long relevantHistoryId;

    @Column(name = "refund_service_id")
    private Long refundServiceId;

    @Column(name = "content_json")
    private String contentJson;

    @Column(name = "yb_class_enum")
    private String ybClassEnum;

    @Column(name = "print_count")
    private Integer printCount;

    /**
     * 绑定就诊诊断主键有序串（有序；首位为主诊断 {@code adm_encounter_diagnosis.id}）。
     */
    @Column(name = "encounter_diagnosis_ids", length = 2000)
    private String encounterDiagnosisIds;

    @Column(name = "therapy_enum")
    private String therapyEnum;

    @Column(name = "rate_code")
    private String rateCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "generate_source_enum")
    private String generateSourceEnum;

    @Column(name = "sort_number")
    private Integer sortNumber;

    @Column(name = "remark")
    private String remark;

    @Column(name = "entrust_content")
    private String entrustContent;

    /**
     * 主手术标志（医师申请；空则病案首页补；非护士执行职责）。
     */
    @Column(name = "main_procedure_flag")
    private Boolean mainProcedureFlag;

    /**
     * 手术级别字典码（医师申请；空则病案首页补；非护士执行职责）。
     */
    @Column(name = "procedure_level_code")
    private String procedureLevelCode;

    /**
     * 切口类别字典码（医师申请可预填；护士执行另见 cln_procedure）。
     */
    @Column(name = "incision_type_code")
    private String incisionTypeCode;

    /**
     * 麻醉方法字典码（医师申请可预填；真源宜麻醉侧/首页）。
     */
    @Column(name = "anesthesia_method_code")
    private String anesthesiaMethodCode;

    /**
     * 手术时间（医师申请可填；执行拷贝至 {@code cln_procedure}，护士可改）。
     */
    @Column(name = "procedure_time")
    private LocalDateTime procedureTime;

    /**
     * 主刀医师；申请预填，执行写入 {@code cln_procedure_performer}。
     */
    @Column(name = "surgeon_id")
    private Long surgeonId;

    /**
     * 一助；申请预填，执行写入 performer。
     */
    @Column(name = "first_assistant_id")
    private Long firstAssistantId;

    /**
     * 二助；申请预填，执行写入 performer。
     */
    @Column(name = "second_assistant_id")
    private Long secondAssistantId;

    /**
     * 配合护士；申请预填，执行写入 performer。
     */
    @Column(name = "nurse_id")
    private Long nurseId;

    /**
     * 麻醉医师；申请预填，执行写入 performer。
     */
    @Column(name = "anesthetist_id")
    private Long anesthetistId;

    /**
     * 嵌套麻醉申请单号（手术 AR 关联麻醉 AR）。
     */
    @Column(name = "nested_anesthesia_prescription_no")
    private String nestedAnesthesiaPrescriptionNo;

    /**
     * 草稿创建断言。
     *
     * @throws BusinessException 参数非法
     */
    public void assertDraftCreatable() {
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 encounterId 不能为空");
        }
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 patientId 不能为空");
        }
        if (this.activityId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 activityId 不能为空");
        }
        if (this.requesterId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 requesterId 不能为空");
        }
        if (this.categoryEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 categoryEnum 不能为空");
        }
        if (this.therapyEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 therapyEnum 不能为空");
        }
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 quantity 必须大于 0");
        }
        if (StringUtils.isEmpty(this.unitCode)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "创建服务申请草稿时 unitCode 不能为空");
        }
        this.assertLongTermRateCodePresent();
    }

    /**
     * 草稿可编辑断言（持久化值须等于 {@link RequestStatus#DRAFT} 的 {@link RequestStatus#getCode()}）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertDraftEditable() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.DRAFT.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅草稿态允许修改服务申请");
        }
    }

    /**
     * 草稿可删除断言。
     *
     * <p>已收费不可删等跨域门控由应用服务调用前保证。</p>
     *
     * @throws BusinessException 状态不允许
     */
    public void assertDraftRemovable() {
        this.assertDraftEditable();
    }

    /**
     * 签发断言（DRAFT -> ACTIVE）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertSignable() {
        this.assertDraftEditable();
        this.assertLongTermRateCodePresent();
    }

    /**
     * 长期诊疗须指定频次（与住院校对/执行列表 BR-IP-EXE-003 一致）。
     */
    public void assertLongTermRateCodePresent() {
        if (TherapyType.LONG_TERM.getCode().equals(this.therapyEnum) && StringUtils.isEmpty(this.rateCode)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "长期诊疗医嘱须指定频次");
        }
    }

    /**
     * 签退断言（ACTIVE -> DRAFT）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertWithdrawable() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送态允许签退服务申请");
        }
    }

    /**
     * 校对断言（ACTIVE -> VERIFIED，住院护士校对路径）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertVerifiable() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送态允许校对服务申请");
        }
    }

    /**
     * 门诊收费完成断言（ACTIVE -> COMPLETED，与住院校对 VERIFIED 分离）。
     *
     * @throws BusinessException 状态不允许
     */
    public void ensureCompletableAfterOutpatientCharge() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送状态允许门诊收费完成服务申请");
        }
    }

    /**
     * 退回草稿断言（ACTIVE/VERIFIED/COMPLETED -> DRAFT）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertRejectable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.ACTIVE.getCode())
            && !this.statusEnum.equals(RequestStatus.VERIFIED.getCode())
            && !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送/已校对允许退回草稿");
        }
    }

    /**
     * 停嘱断言：住院长期停嘱须已开嘱校对（{@link RequestStatus#VERIFIED}）；禁止 ACTIVE 直接停嘱。
     *
     * <p>门诊完成态 {@link RequestStatus#COMPLETED} 仍允许停嘱（与住院校对路径分离）。</p>
     *
     * @throws BusinessException 状态不允许
     */
    public void assertStoppable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        // 住院路径：仅已开嘱校对可停；门诊 COMPLETED 保留可停。
        if (!this.statusEnum.equals(RequestStatus.VERIFIED.getCode())
            && !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已校对允许停嘱");
        }
    }

    /**
     * 断言当前为已停嘱的长期诊疗（改停嘱时间等；不检查停嘱校对痕迹）。
     *
     * @throws BusinessException 状态或长临不允许
     */
    public void assertLongTermStopped() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            // 临时诊疗不可操作停嘱轴。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅长期诊疗医嘱可操作停嘱");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            // 非 STOPPED 不可操作停嘱轴，防止误改在执医嘱。
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已停嘱的长期医嘱可操作停嘱");
        }
    }

    /**
     * 取消停嘱断言：须为长期、已停嘱，且护士尚未完成停嘱校对。
     *
     * @throws BusinessException 状态或长临不允许
     */
    public void assertCancelStopAllowed() {
        assertLongTermStopped();
        // 已停嘱校对时医生禁止取消停嘱，须护士先取消停嘱校对。
        if (this.stopPerformerCheckId != null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "已停嘱校对，请先取消停嘱校对后再取消停嘱");
        }
    }

    /**
     * 断言可停嘱校对：须已停嘱、长期、且尚未写入停嘱校对痕迹。
     */
    public void assertStopVerifiable() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅长期诊疗医嘱可停嘱校对");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已停嘱状态可停嘱校对");
        }
        // 已写过停嘱校对痕迹则拒绝重复校对。
        if (this.stopPerformerCheckId != null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "该医嘱已完成停嘱校对");
        }
    }

    /**
     * 断言可取消停嘱校对：须长期、已停嘱且已有停嘱校对痕迹。
     */
    public void assertStopVerifyCancellable() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅长期诊疗医嘱可取消停嘱校对");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已停嘱状态可取消停嘱校对");
        }
        // 未停嘱校对则无痕迹可清。
        if (this.stopPerformerCheckId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "该医嘱尚未停嘱校对");
        }
    }

    /**
     * 取消断言（DRAFT/ACTIVE/VERIFIED/COMPLETED → CANCELLED）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertCancelable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.DRAFT.getCode())
            && !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())
            && !this.statusEnum.equals(RequestStatus.VERIFIED.getCode())
            && !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅草稿/已发送/已校对/已完成允许取消");
        }
    }

    /**
     * 执行派生创建断言。
     *
     * @throws BusinessException 参数非法
     */
    public void assertExecutionDerivedCreatable() {
        if (StringUtils.isEmpty(this.basedOnTable) || this.basedOnId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "执行派生创建需填写 basedOnTable/basedOnId");
        }
        if (this.generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "执行派生创建需填写 generateSourceEnum");
        }
    }

    /**
     * 执行派生可逻辑删除断言（COMPLETED 态执行复制/绑定派生，AS-IP-NUR-012 BR-IP-CXL-013）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertExecutionDerivedRemovable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已完成态执行派生服务申请允许逻辑删除");
        }
    }

    /**
     * 护士划价可撤销断言（VERIFIED + nurse-pricing，AS-IP-NUR-005 IF-IP-PRC-06 BR-IP-PRC-022）。
     *
     * @throws BusinessException 来源或状态不允许
     */
    public void assertNursePricingRemovable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请已删除");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.VERIFIED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已校对态护士划价诊疗申请允许撤销");
        }
        if (this.generateSourceEnum == null
            || !GenerateSource.NURSE_PRICING.getCode().equals(this.generateSourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅护士划价来源诊疗申请允许撤销");
        }
    }

    /**
     * 初始化草稿状态。
     */
    public void initDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
    }

    /**
     * 初始化已发送状态（挂号登记等不经住院校对的路径）。
     */
    public void initActive() {
        this.statusEnum = RequestStatus.ACTIVE.getCode();
    }

    /**
     * 初始化已校对状态（护士划价：跳过校对 Tab，创建即已校对）。
     */
    public void initVerified() {
        this.statusEnum = RequestStatus.VERIFIED.getCode();
    }

    /**
     * 初始化已完成状态（执行复制/绑定派生）。
     */
    public void initCompleted() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
    }

    /**
     * 草稿 -> 已发送。
     */
    public void draftToActive() {
        this.statusEnum = RequestStatus.ACTIVE.getCode();
    }

    /**
     * 已发送 -> 草稿（撤回发送）。
     */
    public void activeToDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
        // 签退回草稿：草稿态不得保留签发列。
        this.reqAuthoredTime = null;
    }

    /**
     * 已发送 -> 已校对（住院护士校对路径，与门诊收费 COMPLETED 分离）。
     */
    public void activeToVerified() {
        this.statusEnum = RequestStatus.VERIFIED.getCode();
    }

    /**
     * 已发送 -> 已完成（门诊收费收口、Procedure 执行完成等；不进住院校对 VERIFIED 语义）。
     */
    public void activeToCompleted() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
    }

    /**
     * 已发送/已完成 -> 草稿（回退重编）。
     */
    public void activeOrCompletedToDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
        // 驳回回草稿：草稿态不得保留签发列。
        this.reqAuthoredTime = null;
    }

    /**
     * 流转为已停止：写入停嘱时间与停嘱人；停嘱校对应为空（待护士校对）。
     *
     * @param stopTime  停嘱时间（{@code stop_time}）
     * @param stopperId 停嘱人（{@code stopper_id}）
     */
    public void toStopped(LocalDateTime stopTime, Long stopperId) {
        this.stopTime = stopTime;
        this.stopperId = stopperId;
        // 新停嘱视为未停嘱校对，须护士重新校对。
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
        this.statusEnum = RequestStatus.STOPPED.getCode();
    }

    /**
     * 取消停嘱：按开嘱校对痕迹恢复为已校对或已发送，并清空停嘱轴与停嘱校对痕迹（BR-IP-ORD-025 / BR-SRQ-016a）。
     */
    public void fromStopped() {
        // 护士已开嘱校对过则恢复 VERIFIED，否则恢复 ACTIVE（BR-IP-ORD-025 / BR-SRQ-016a）。
        if (this.performerCheckId != null) {
            this.statusEnum = RequestStatus.VERIFIED.getCode();
        } else {
            this.statusEnum = RequestStatus.ACTIVE.getCode();
        }
        // 取消停嘱清 stop_time / stopper_id / 停嘱校对痕迹，不改动计划结束列。
        this.stopTime = null;
        this.stopperId = null;
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
    }

    /**
     * 断言当前为已停嘱，可供门诊退费申请撤销时恢复（不校验长期/停嘱校对门禁）。
     *
     * @throws BusinessException 非 {@link RequestStatus#STOPPED} 时
     */
    public void ensureStoppedForOutpatientRefundRevoke() {
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "仅已停嘱的诊疗申请允许因撤销退费申请而恢复");
        }
    }

    /**
     * 断言本行为负向退诊疗申请（含 {@code refund_service_id}），可供门诊退费申请撤销时软删。
     *
     * @throws BusinessException 非负向退诊疗单时
     */
    public void ensureOpenRefundServiceOrderForVoid() {
        if (this.refundServiceId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "非退诊疗负向申请不允许作废");
        }
    }

    /**
     * 断言本行可因门诊代煎费退款终态而软删：须为停嘱态（申请退费未完成路径留下的 companion）。
     *
     * @throws BusinessException 非 {@code STOPPED} 时
     */
    public void ensureStoppedForOutpatientDecoctionRefundSoftDelete() {
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "仅已停嘱的代煎 companion 允许在代煎费退款后软删");
        }
    }

    /**
     * 逻辑删除诊疗服务申请主档（门诊退费申请撤销负向单等路径）。
     *
     * @param operatorId 操作人主键
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 停嘱校对：仅写停嘱校对人/时间，不改 {@link RequestStatus#STOPPED}。
     *
     * @param stopPerformerCheckId 停嘱校对人
     * @param stopCheckTime        停嘱校对时间
     */
    public void markStopChecked(Long stopPerformerCheckId, LocalDateTime stopCheckTime) {
        assertStopVerifiable();
        // 校对人必填，禁止空痕迹。
        if (stopPerformerCheckId == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "停嘱校对人不能为空");
        }
        this.stopPerformerCheckId = stopPerformerCheckId;
        this.stopCheckTime = stopCheckTime;
    }

    /**
     * 取消停嘱校对：清空停嘱校对痕迹，状态仍为 {@link RequestStatus#STOPPED}。
     */
    public void clearStopChecked() {
        assertStopVerifyCancellable();
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
    }

    /**
     * 断言可改业务时间：开立任意存在行；停嘱须长期且已 STOPPED（BR-SRQ-016b）。
     *
     * @param timeKind 时间种类 code
     */
    public void assertBusinessTimeChangeAllowed(String timeKind) {
        RequestBusinessTimeKind kind = RequestBusinessTimeKind.fromCodeOrNull(timeKind);
        // 种类非法或试图改签发：拒绝。
        if (kind == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "不可修改该时间");
        }
        // 改停嘱时间仅已停嘱长期行可执行；已停嘱校对仍可改时且保留校对痕迹。
        if (RequestBusinessTimeKind.STOP.equals(kind)) {
            assertLongTermStopped();
        }
    }

    /**
     * 改开立或停嘱时间；不改开立人/停嘱人/签发时间（BR-SRQ-016b）。
     *
     * <p>出院类医嘱：列表「时间」与计划出院真源 {@code occurrence_start_time} 同源；
     * 取消校对回草稿后医生改开立时间须同步计划出院时间，否则再校对仍用旧计划时刻。</p>
     *
     * @param timeKind 时间种类 code
     * @param newTime  新业务时间
     */
    public void changeBusinessTime(String timeKind, LocalDateTime newTime) {
        assertBusinessTimeChangeAllowed(timeKind);
        // 新时间必填，避免误清空业务时间轴。
        if (newTime == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "新业务时间不能为空");
        }
        RequestBusinessTimeKind kind = RequestBusinessTimeKind.fromCodeOrNull(timeKind);
        // OPENED：覆盖开立列；出院嘱同步计划出院时间（occurrence_start_time 业务真源）
        if (RequestBusinessTimeKind.OPENED.equals(kind)) {
            this.reqOpenedTime = newTime;
            // 出院类：取消校对回草稿后医生改时间须同步计划出院，否则再校对仍用旧计划时刻
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_DISCHARGE.equals(this.categoryEnum)) {
                this.occurrenceStartTime = newTime;
            }
            return;
        }
        // STOP：仅覆盖停嘱列。
        this.stopTime = newTime;
    }

    /**
     * 流转为已取消。
     */
    public void toCancelled() {
        this.statusEnum = RequestStatus.CANCELLED.getCode();
    }

    /**
     * 初始化退诊疗负向申请单：待退态（与药/耗负向单口径一致）。
     */
    public void initAsRefundNegativeOrder() {
        this.statusEnum = RequestStatus.CANCELLED.getCode();
    }

    /**
     * 退诊疗负向单处方号：在原处方号前加 {@code T} 前缀（与药/耗 BR-MRQ-013 / BR-DR-007 同构）。
     *
     * @param sourcePrescriptionNo 原正向诊疗申请处方号
     */
    public void markAsRefundFromPrescription(String sourcePrescriptionNo) {
        if (StringUtils.isEmpty(sourcePrescriptionNo)) {
            return;
        }
        this.prescriptionNo = "T" + sourcePrescriptionNo.trim();
    }

    /**
     * 打印次数增量累加断言。
     *
     * @param increaseBy 增量
     */
    public void assertPrintCountIncreasable(Integer increaseBy) {
        if (increaseBy == null || increaseBy <= 0) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "打印次数增量必须大于 0");
        }
    }
}

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

package com.openhis.health.domain.workflow.servicerequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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

    @Column(name = "authored_time")
    private LocalDateTime authoredTime;

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

    @Column(name = "occurrence_end_time")
    private LocalDateTime occurrenceEndTime;

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

    /** 诊断定义主键（列 {@code condition_definition_id}，即 {@code cln_condition_definition.id}）。 */
    @Column(name = "condition_definition_id")
    private Long conditionDefinitionId;

    @Column(name = "encounter_diagnosis_id")
    private Long encounterDiagnosisId;

    @Column(name = "therapy_enum")
    private String therapyEnum;

    @Column(name = "sign_code")
    private String signCode;

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
        if (StringUtils.isBlank(this.unitCode)) {
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
        if (TherapyType.LONG_TERM.getCode().equals(this.therapyEnum) && StringUtils.isBlank(this.rateCode)) {
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
     * 停嘱断言（ACTIVE/VERIFIED/COMPLETED -> STOPPED）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertStoppable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.ACTIVE.getCode())
            && !this.statusEnum.equals(RequestStatus.VERIFIED.getCode())
            && !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送/已校对允许停嘱");
        }
    }

    /**
     * 取消断言（ACTIVE/COMPLETED -> CANCELLED）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertCancelable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.ACTIVE.getCode())
            && !this.statusEnum.equals(RequestStatus.VERIFIED.getCode())
            && !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "仅已发送/已校对允许取消");
        }
    }

    /**
     * 执行派生创建断言。
     *
     * @throws BusinessException 参数非法
     */
    public void assertExecutionDerivedCreatable() {
        if (StringUtils.isBlank(this.basedOnTable) || this.basedOnId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "执行派生创建需填写 basedOnTable/basedOnId");
        }
        if (this.generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "执行派生创建需填写 generateSourceEnum");
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
    }

    /**
     * 流转为已停止。
     */
    public void toStopped() {
        this.statusEnum = RequestStatus.STOPPED.getCode();
    }

    /**
     * 流转为已取消。
     */
    public void toCancelled() {
        this.statusEnum = RequestStatus.CANCELLED.getCode();
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

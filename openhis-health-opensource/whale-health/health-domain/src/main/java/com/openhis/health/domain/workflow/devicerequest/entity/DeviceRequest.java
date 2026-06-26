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

package com.openhis.health.domain.workflow.devicerequest.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材请求聚合根。
 *
 * <p>
 * 对应表：wkf_device_request。
 * </p>
 *
 * <p>
 * 边界：本实体只承载“耗材请求事实与状态”。库存扣减/回补、发放单生成/回退、收费结算等由应用服务编排。
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_device_request")
public class DeviceRequest extends BaseAuditedEntity {

    @Column(name = "bus_no")
    private String busNo;

    @Column(name = "prescription_no")
    private String prescriptionNo;

    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "intent_code")
    private String intentCode;

    @Column(name = "category_enum")
    private String categoryEnum;

    @Column(name = "priority_enum")
    private String priorityEnum;

    @Column(name = "perform_flag")
    private Boolean performFlag;

    @Column(name = "group_no")
    private String groupNo;

    @Column(name = "device_type_code")
    private String deviceTypeCode;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_code")
    private String unitCode;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "device_def_id", nullable = false)
    private Long deviceDefId;

    @Column(name = "device_specifications")
    private String deviceSpecifications;

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "perform_location")
    private Long performLocation;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "rate_code")
    private String rateCode;

    @Column(name = "use_time")
    private LocalDateTime useTime;

    @Column(name = "use_start_time")
    private LocalDateTime useStartTime;

    @Column(name = "use_end_time")
    private LocalDateTime useEndTime;

    @Column(name = "use_timing")
    private String useTiming;

    @Column(name = "req_authored_time")
    private LocalDateTime reqAuthoredTime;

    @Column(name = "performer_enum")
    private String performerEnum;

    @Column(name = "performer_id")
    private Long performerId;

    @Column(name = "perform_org_id")
    private Long performOrgId;

    @Column(name = "as_need_flag")
    private Boolean asNeedFlag;

    @Column(name = "as_need_reason")
    private String asNeedReason;

    @Column(name = "contract_code")
    private String contractCode;

    @Column(name = "refund_device_id")
    private Long refundDeviceId;

    @Column(name = "yb_class_enum")
    private String ybClassEnum;

    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    /** 诊断定义主键（列 {@code condition_definition_id}，即 {@code cln_condition_definition.id}）。 */
    @Column(name = "condition_definition_id")
    private Long conditionDefinitionId;

    @Column(name = "encounter_diagnosis_id")
    private Long encounterDiagnosisId;

    @Column(name = "observation_id_json")
    private String observationIdJson;

    @Column(name = "support_info")
    private String supportInfo;

    @Column(name = "based_on_table")
    private String basedOnTable;

    @Column(name = "based_on_id")
    private Long basedOnId;

    @Column(name = "generate_source_enum")
    private String generateSourceEnum;

    @Column(name = "sort_number")
    private Integer sortNumber;

    @Column(name = "remark")
    private String remark;

    /**
     * 草稿可编辑性断言。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertDraftEditable() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.DRAFT.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT,
                "仅草稿态允许修改/删除耗材请求");
        }
    }

    /**
     * 签发断言（DRAFT -> ACTIVE）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertSignable() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.DRAFT.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅草稿态允许签发耗材请求");
        }
    }

    /**
     * 签退断言（ACTIVE -> DRAFT）。
     *
     * <p>
     * 费用已收费不可签退等跨域约束由应用服务在调用前完成。
     * </p>
     *
     * @throws BusinessException 状态不允许
     */
    public void assertSignOffAllowed() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅已发送态允许签退耗材请求");
        }
    }

    /**
     * 执行确认断言（ACTIVE/DRAFT -> COMPLETED）。
     *
     * <p>
     * 发放链路就绪等跨域前置由应用服务编排在调用前保证。
     * </p>
     *
     * @throws BusinessException 状态不允许
     */
    public void assertExecutable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材请求状态不能为空");
        }
        if (!this.statusEnum.equals(RequestStatus.DRAFT.getCode())
            && !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅草稿/已发送态允许执行确认");
        }
    }

    /**
     * 校验创建草稿的最小必填。
     *
     * @throws BusinessException 参数非法
     */
    public void assertRequiredForCreate() {
        if (this.deviceDefId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材定义标识不能为空");
        }
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (this.quantity == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材数量不能为空");
        }
        if (this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材数量必须大于 0");
        }
    }

    /**
     * 断言生成已完成请求的来源合法。
     *
     * @throws BusinessException 参数非法
     */
    public void assertSourceAllowedForCompletedCreate() {
        if (this.generateSourceEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "生成来源不能为空");
        }
    }

    /**
     * 初始化为草稿。
     */
    public void initDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
    }

    /**
     * 草稿签发为已发送。
     */
    public void draftToActive() {
        this.statusEnum = RequestStatus.ACTIVE.getCode();
    }

    /**
     * 已发送签退回草稿。
     */
    public void activeToDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
    }

    /**
     * 迁移为已完成。
     */
    public void toCompleted() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
    }

    /**
     * 断言负向耗材申请可在药房退药闭环后完结为已完成。
     */
    public void ensureCompletableAfterPharmacyRefund() {
        if (this.refundDeviceId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_RULE_CONFLICT, "非退耗负向申请不允许执行退药完结");
        }
        if (RequestStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "退耗申请已完成，请勿重复操作");
        }
    }

    /**
     * 药房退药闭环完成：负向耗材申请推进为已完成。
     */
    public void toCompletedAfterPharmacyRefund() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
    }

    /**
     * 断言可由门诊收费链路推进为已完成（仅已发送态）。
     */
    public void assertCompletableAfterOutpatientCharge() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.ACTIVE.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅已发送态允许门诊收费完成耗材请求");
        }
    }

    /**
     * 判断当前耗材请求是否处于草稿状态（statusEnum=DRAFT），用于 QueryService 的门控结果返回。
     *
     * @return 草稿态返回 true
     */
    public boolean isDraftEditable() {
        return this.statusEnum != null && this.statusEnum.equals(RequestStatus.DRAFT.getCode());
    }

    /**
     * 判断当前耗材请求是否处于已发送状态（statusEnum=ACTIVE），用于 QueryService 的签退门控结果返回。
     *
     * @return 已发送态返回 true
     */
    public boolean isSignOffAllowed() {
        return this.statusEnum != null && this.statusEnum.equals(RequestStatus.ACTIVE.getCode());
    }

    /**
     * 判断当前耗材请求是否处于已完成状态（statusEnum=COMPLETED），用于 QueryService 的退费门控结果返回。
     *
     * @return 已完成态返回 true
     */
    public boolean isCompleted() {
        return this.statusEnum != null && this.statusEnum.equals(RequestStatus.COMPLETED.getCode());
    }

    /**
     * 断言当前耗材请求处于已完成状态（statusEnum=COMPLETED），仅允许在已完成态执行撤销执行或撤销请求操作。
     *
     * <p>
     * DM-WKF-003 §6.1 撤销前置条件：请求须已完成才可撤销。
     * </p>
     *
     * @throws BusinessException 状态非已完成
     */
    public void assertCompleted() {
        if (this.statusEnum == null || !this.statusEnum.equals(RequestStatus.COMPLETED.getCode())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅已完成态允许执行撤销操作");
        }
    }

    /**
     * 护士划价耗材请求可撤销断言（COMPLETED + nurse-pricing，AS-IP-NUR-005 IF-IP-PRC-06 BR-IP-PRC-022）。
     *
     * @throws BusinessException 来源或状态不允许
     */
    public void assertNursePricingRevocable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材请求已删除");
        }
        this.assertCompleted();
        if (this.generateSourceEnum == null
            || !GenerateSource.NURSE_PRICING.getCode().equals(this.generateSourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "仅护士划价来源耗材请求允许撤销");
        }
    }

    /**
     * 校验并规范化退耗材处方号前缀（产品约定）。
     *
     * @param sourcePrescriptionNo 原单处方号
     */
    public void markAsRefundFromPrescription(String sourcePrescriptionNo) {
        if (StringUtils.isBlank(sourcePrescriptionNo)) {
            return;
        }
        this.prescriptionNo = "T" + sourcePrescriptionNo;
    }
}


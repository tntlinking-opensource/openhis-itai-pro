/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.entity;

import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医保协同单聚合根（{@code yb_coordination}，对齐 06-协同单与 Worker §2）。
 *
 * <p>承载一次医保平台/本地编排的全生命周期；不变式：msgid 非空、状态机合法、
 * 已完成单不可再迁移；乐观锁 {@link #version} 防 Worker 并发写冲突。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_coordination")
public class YbCoordination extends BaseAuditedEntity {

    /** 业务类型（存 {@link YbCoordinationBizType#getCode()}）。 */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 医保方案编码（如吉林 CHS 方案），可为空时由 Worker 默认。 */
    @Column(name = "scheme_code", nullable = false, length = 32)
    private String schemeCode;

    /** 协同单状态（存 {@link YbCoordinationState#getCode()}）。 */
    @Column(name = "state", nullable = false, length = 32)
    private String state;

    /** 业务锚点主键（如结算单、入院单等业务实体 id）。 */
    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    /** 关联账户主键，结算类并发检测维度之一。 */
    @Column(name = "account_id")
    private Long accountId;

    /** 费用性质（合同）主键，决定医保/自费口径。 */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 父协同单主键，补偿/撤销链场景指向原单。 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 支付单主键，门诊/住院结算类业务并发门禁维度。 */
    @Column(name = "payment_id")
    private Long paymentId;

    /** 支付单业务号，便于对账与日志关联。 */
    @Column(name = "payment_no", length = 64)
    private String paymentNo;

    /** 就诊主键，须与 patientId 同属一次接诊。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 患者主键。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 平台报文 msgid，全局唯一标识一次医保交互。 */
    @Column(name = "msgid", nullable = false, length = 64)
    private String msgid;

    /** 当前或最近调用的 infno 交易号。 */
    @Column(name = "infno", length = 16)
    private String infno;

    /** 平台返回的结算 id（setl_id），成功态写入。 */
    @Column(name = "platform_setl_id", length = 64)
    private String platformSetlId;

    /** 调度优先级（存 {@link YbCoordinationPriority#getCode()}）。 */
    @Column(name = "priority", nullable = false, length = 8)
    private String priority;

    /** 是否阻塞本地结算流程直至协同完成。 */
    @Column(name = "blocking", nullable = false)
    private Boolean blocking;

    /** 发起请求 JSON 快照，供审计与重试回放。 */
    @Column(name = "request_snapshot", columnDefinition = "TEXT")
    private String requestSnapshot;

    /** 平台/本地回执 JSON 快照。 */
    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    /** 平台返回金额（对账辅助字段）。 */
    @Column(name = "platform_amount")
    private BigDecimal platformAmount;

    /** 已重试次数，Worker 退避策略依据。 */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    /** 下次允许调度时间，null 表示立即可运行。 */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 最近错误码（平台或本地）。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 最近错误描述。 */
    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    /** 是否已转人工工单处理。 */
    @Column(name = "manual_ticket", nullable = false)
    private Boolean manualTicket;

    /** 操作员主键（发起协同的窗口人员）。 */
    @Column(name = "operator_id")
    private Long operatorId;

    /** JPA 乐观锁版本号。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 解析业务类型枚举。
     *
     * @return 业务类型枚举，code 非法时可能为 null
     */
    public YbCoordinationBizType bizTypeEnum() {
        return YbCoordinationBizType.fromCode(this.bizType);
    }

    /**
     * 解析状态枚举。
     *
     * @return 状态枚举，code 非法时可能为 null
     */
    public YbCoordinationState stateEnum() {
        return YbCoordinationState.fromCode(this.state);
    }

    /**
     * 断言落库前必填字段完整（bizType、state、msgid）。
     *
     * @throws BusinessException INS_012 必填缺失
     */
    public void assertPersistable() {
        if (StringUtils.isBlank(this.bizType)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单业务类型不能为空");
        }
        if (StringUtils.isBlank(this.state)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单状态不能为空");
        }
        if (StringUtils.isBlank(this.msgid)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单 msgid 不能为空");
        }
    }

    /**
     * 校验是否允许迁移至目标状态（状态机不变式）。
     *
     * @param target 目标状态
     * @throws BusinessException INS_012 当前/目标非法、已完成不可迁、禁止重复平台调用等
     */
    public void assertTransitionTo(YbCoordinationState target) {
        YbCoordinationState current = this.stateEnum();
        if (current == null || target == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单状态非法");
        }
        // 已完成终态：任何后续迁移均拒绝，防止重复结算
        if (YbCoordinationState.COMPLETED.equals(current)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "已完成协同单不可再转移");
        }
        // UNKNOWN 禁止直接回到平台运行，须先走恢复/补偿路径
        if (YbCoordinationState.UNKNOWN.equals(current) && YbCoordinationState.PLATFORM_RUNNING.equals(target)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "UNKNOWN 状态禁止重新发起平台结算");
        }
        // 平台已成功：禁止再次进入平台运行，避免重复扣款
        if (YbCoordinationState.PLATFORM_OK.equals(current) && YbCoordinationState.PLATFORM_RUNNING.equals(target)) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "平台已成功，禁止重复平台调用");
        }
    }

    /**
     * 校验并应用目标状态。
     *
     * @param target 目标状态
     */
    public void applyState(YbCoordinationState target) {
        this.assertTransitionTo(target);
        this.state = target.getCode();
    }
}

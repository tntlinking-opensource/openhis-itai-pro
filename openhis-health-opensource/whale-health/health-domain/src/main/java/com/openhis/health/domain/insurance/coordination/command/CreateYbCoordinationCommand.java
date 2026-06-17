/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.command;

import com.openhis.health.domain.share.enums.InsuranceSchemeCode;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建医保协同单命令。
 *
 * <p>由应用层组装后调用 {@link com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService#create}。</p>
 */
@Getter
@Setter
public class CreateYbCoordinationCommand {

    /** 协同业务类型。 */
    private YbCoordinationBizType bizType;
    /** 医保方案编码。 */
    private InsuranceSchemeCode schemeCode;
    /** 业务锚点主键。 */
    private Long bizId;
    /** 关联账户主键。 */
    private Long accountId;
    /** 费用性质主键。 */
    private Long contractId;
    /** 支付单主键（结算类必填）。 */
    private Long paymentId;
    /** 支付单业务号。 */
    private String paymentNo;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 操作员主键。 */
    private Long operatorId;
    /** 调度优先级，null 时默认 P0。 */
    private YbCoordinationPriority priority;
    /** 是否阻塞本地流程直至协同完成。 */
    private boolean blocking;
    /** 父协同单主键（补偿/撤销链）。 */
    private Long parentId;
    /** 发起请求 JSON 快照。 */
    private String requestSnapshot;
    /** 平台报文 msgid，须全局唯一。 */
    private String msgid;
}

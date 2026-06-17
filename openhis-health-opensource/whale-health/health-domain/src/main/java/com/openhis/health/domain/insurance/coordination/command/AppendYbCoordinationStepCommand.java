/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 追加医保协同单步骤命令。
 *
 * <p>记录单次 infno 调用的请求/回执快照，由 Worker 或应用层在平台往返后写入。</p>
 */
@Getter
@Setter
public class AppendYbCoordinationStepCommand {

    /** 所属协同单主键。 */
    private Long coordinationId;
    /** 步骤序号。 */
    private Integer stepOrder;
    /** infno 交易号。 */
    private String infno;
    /** 本步骤 msgid。 */
    private String msgid;
    /** 步骤状态。 */
    private String state;
    /** 请求 JSON 快照。 */
    private String requestSnapshot;
    /** 回执 JSON 快照。 */
    private String responseSnapshot;
    /** 错误码。 */
    private String errorCode;
    /** 错误描述。 */
    private String errorMsg;
}

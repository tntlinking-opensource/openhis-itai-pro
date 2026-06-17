/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.insurance.coordination.command;

import com.openhis.health.domain.share.enums.YbCoordinationState;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 迁移医保协同单状态命令。
 *
 * <p>含目标状态与可选平台回执、错误信息与重试窗口字段；非空字段才会覆盖聚合根已有值。</p>
 */
@Getter
@Setter
public class TransitionYbCoordinationCommand {

    /** 协同单主键。 */
    private Long coordinationId;
    /** 目标状态。 */
    private YbCoordinationState targetState;
    /** 错误码。 */
    private String errorCode;
    /** 错误描述。 */
    private String errorMsg;
    /** 平台结算 id。 */
    private String platformSetlId;
    /** 回执 JSON 快照。 */
    private String responseSnapshot;
    /** 下次允许调度时间。 */
    private LocalDateTime nextRetryAt;
    /** 已重试次数。 */
    private Integer retryCount;
}

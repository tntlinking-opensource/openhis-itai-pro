/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.lis.command;

import com.openhis.health.integration.lis.dto.LisAdviceApplyDetailLine;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 检验外发命令（IF-DOR-15 / IF-IP-DOR-15）。
 */
@Getter
@Setter
public class LisAdviceApplyCommand {

    private Long encounterId;
    private Long patientId;
    private Long requestId;
    private List<LisAdviceApplyDetailLine> details;
}

/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.pacs.command;

import com.openhis.health.integration.pacs.dto.PacsAdviceApplyDetailLine;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 检查外发命令（IF-DOR-15 / IF-IP-DOR-15）。
 */
@Getter
@Setter
public class PacsAdviceApplyCommand {

    private Long encounterId;
    private Long patientId;
    private Long requestId;
    private List<PacsAdviceApplyDetailLine> details;
}

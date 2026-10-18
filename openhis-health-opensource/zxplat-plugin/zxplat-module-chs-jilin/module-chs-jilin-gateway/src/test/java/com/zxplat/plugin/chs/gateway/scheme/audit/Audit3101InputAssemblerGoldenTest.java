/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit;

import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.domain.audit.command.AuditAnalyze3101Command;
import com.zxplat.plugin.chs.domain.audit.command.AuditDiagnoseCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditEncounterCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditOrderCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditPatientCommand;
import com.zxplat.plugin.chs.gateway.scheme.audit.assembler.Audit3101InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101Input;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 【3101】Assembler 关键节点与联调发送形态（数组）对齐 */
class Audit3101InputAssemblerGoldenTest {

    @Test
    void assemble_patientEncounterAsArrays() {
        AuditAnalyze3101Command command = new AuditAnalyze3101Command();
        command.setSyscode("ims");
        command.setTrigScen("2");
        AuditPatientCommand patient = new AuditPatientCommand();
        patient.setPatnId("149402417");
        patient.setPatnName("公务员退休");
        patient.setGend("1");
        patient.setCurrMdtrtId("149402417");
        AuditEncounterCommand encounter = new AuditEncounterCommand();
        encounter.setMdtrtId("149402417");
        encounter.setMedinsId("H22020300392");
        encounter.setMedinsAdmdvs("220299");
        encounter.setMedinsType("A");
        encounter.setMedinsLv("11");
        AuditDiagnoseCommand diagnose = new AuditDiagnoseCommand();
        diagnose.setDiseCodg("N80.809");
        diagnose.setMaindiseFlag("1");
        encounter.getDiagnoses().add(diagnose);
        AuditOrderCommand order = new AuditOrderCommand();
        order.setRxId("1054440262");
        order.setHilistCode("ZD02AAK0062010305555");
        order.setCnt(new BigDecimal("1"));
        order.setPric(new BigDecimal("31.92"));
        order.setSumamt(new BigDecimal("31.92"));
        encounter.getOrders().add(order);
        patient.getEncounters().add(encounter);
        command.getPatients().add(patient);

        Audit3101Input input = Audit3101InputAssembler.assemble(command);
        assertNotNull(input.getData());
        assertEquals("ims", input.getData().getSyscode());
        assertEquals("2", input.getData().getTrigScen());
        assertEquals(1, input.getData().getPatientDtos().size());
        assertEquals(1, input.getData().getPatientDtos().get(0).getFsiEncounterDtos().size());
        assertNotNull(input.getData().getPatientDtos().get(0).getFsiHisDataDto());
        assertTrue(input.getData().getPatientDtos().get(0).getFsiHisDataDto().isEmpty());
        assertEquals(
            List.of(),
            input.getData().getPatientDtos().get(0).getFsiEncounterDtos().get(0).getFsiOperationDtos());

        String json = JSON.toJSONString(input);
        assertTrue(json.contains("\"patient_dtos\""));
        assertTrue(json.contains("\"fsi_encounter_dtos\""));
        assertTrue(json.contains("\"fsi_operation_dtos\""));
        assertTrue(json.contains("\"fsi_order_dtos\""));
    }
}

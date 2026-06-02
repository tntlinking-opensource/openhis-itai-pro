package com.openhis.whale.mrms.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.mrms.application.request.SaveHomepageRequest;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.DiagnosisLineCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.ProcedureLineCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HomepageRequestMapper {

    private final ObjectMapper objectMapper;

    public HomepageRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HomepageSaveCommand toCommand(SaveHomepageRequest request) {
        HomepageSaveCommand command = new HomepageSaveCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setPsnName(request.getPsnName());
        command.setGendCode(request.getGendCode());
        command.setBrdy(request.getBrdy());
        command.setCertno(request.getCertno());
        command.setNtlyCode(request.getNtlyCode());
        command.setNatyCode(request.getNatyCode());
        command.setPrfsCode(request.getPrfsCode());
        command.setMrgStasCode(request.getMrgStasCode());
        command.setCurrAddr(request.getCurrAddr());
        command.setPsnTel(request.getPsnTel());
        command.setConerName(request.getConerName());
        command.setConerTel(request.getConerTel());
        command.setConerAddr(request.getConerAddr());
        command.setIptNo(request.getIptNo());
        command.setAdmWayCode(request.getAdmWayCode());
        command.setAdmCatyCode(request.getAdmCatyCode());
        command.setAdmCatyName(request.getAdmCatyName());
        command.setDscgCatyCode(request.getDscgCatyCode());
        command.setDscgCatyName(request.getDscgCatyName());
        command.setAdmTime(request.getAdmTime());
        command.setDscgTime(request.getDscgTime());
        command.setActIptDays(request.getActIptDays());
        command.setDscgWayCode(request.getDscgWayCode());
        command.setTransferDeptSummary(request.getTransferDeptSummary());
        command.setDeptDirectorName(request.getDeptDirectorName());
        command.setChfdrName(request.getChfdrName());
        command.setAtddrName(request.getAtddrName());
        command.setIptDrName(request.getIptDrName());
        command.setRespNursName(request.getRespNursName());
        if (request.getExtension() != null && !request.getExtension().isEmpty()) {
            try {
                command.setExtensionJson(objectMapper.writeValueAsString(request.getExtension()));
            } catch (Exception ignored) {
                // 扩展字段解析失败时忽略，不阻断保存
            }
        }
        if (request.getDiagnoses() != null) {
            List<DiagnosisLineCommand> lines = new ArrayList<>();
            for (SaveHomepageRequest.DiagnosisLineRequest line : request.getDiagnoses()) {
                DiagnosisLineCommand cmd = new DiagnosisLineCommand();
                cmd.setDiagTypeCode(line.getDiagTypeCode());
                cmd.setSortNo(line.getSortNo() == null ? 0 : line.getSortNo());
                cmd.setDiagCode(line.getDiagCode());
                cmd.setDiagName(line.getDiagName());
                cmd.setAdmCondCode(line.getAdmCondCode());
                lines.add(cmd);
            }
            command.setDiagnoses(lines);
        }
        if (request.getProcedures() != null) {
            List<ProcedureLineCommand> lines = new ArrayList<>();
            for (SaveHomepageRequest.ProcedureLineRequest line : request.getProcedures()) {
                ProcedureLineCommand cmd = new ProcedureLineCommand();
                cmd.setSortNo(line.getSortNo() == null ? 0 : line.getSortNo());
                cmd.setOprnOprtCode(line.getOprnOprtCode());
                cmd.setOprnOprtName(line.getOprnOprtName());
                cmd.setOprnDate(line.getOprnDate());
                cmd.setOprnLevelCode(line.getOprnLevelCode());
                cmd.setOprnIncisionCode(line.getOprnIncisionCode());
                cmd.setHealGradeCode(line.getHealGradeCode());
                cmd.setAnstWayCode(line.getAnstWayCode());
                lines.add(cmd);
            }
            command.setProcedures(lines);
        }
        return command;
    }
}

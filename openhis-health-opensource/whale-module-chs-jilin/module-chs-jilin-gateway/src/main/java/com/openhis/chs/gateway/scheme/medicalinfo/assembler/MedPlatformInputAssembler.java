/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5201Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5202Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5203Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5204Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5205Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5206Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5301Input;

import com.openhis.chs.domain.medicalinfo.command.QueryCum5206Command;
import com.openhis.chs.domain.medicalinfo.command.QueryDise5202Command;
import com.openhis.chs.domain.medicalinfo.command.QueryFee5204Command;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspDrug5205Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.openhis.chs.domain.medicalinfo.command.QuerySetl5203Command;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5201Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5202Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5203Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5204Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5205Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5206Input;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5301Input;

/** 5201–5301 平台报文 input.data 组装 */
public final class MedPlatformInputAssembler {

    private MedPlatformInputAssembler() {
    }

    public static Med5201Input assemble5201(QueryMdtrt5201Command command) {
        Med5201Input.Med5201Data data = new Med5201Input.Med5201Data()
            .setPsnNo(command.getPsnNo())
            .setBegntime(command.getBegntime())
            .setEndtime(command.getEndtime())
            .setMedType(StringUtils.defaultIfBlank(command.getMedType(), MedType.GENERAL_OUTPATIENT.getCode()));
        if (isNotEmpty(command.getMdtrtId())) {
            data.setMdtrtId(command.getMdtrtId().trim());
        }
        Med5201Input input = new Med5201Input();
        input.setData(data);
        return input;
    }

    public static Med5202Input assemble5202(QueryDise5202Command command) {
        Med5202Input.Med5202Data data = new Med5202Input.Med5202Data()
            .setMdtrtId(command.getMdtrtId())
            .setPsnNo(command.getPsnNo());
        Med5202Input input = new Med5202Input();
        input.setData(data);
        return input;
    }

    public static Med5203Input assemble5203(QuerySetl5203Command command) {
        Med5203Input.Med5203Data data = new Med5203Input.Med5203Data()
            .setPsnNo(command.getPsnNo())
            .setSetlId(command.getSetlId())
            .setMdtrtId(command.getMdtrtId());
        Med5203Input input = new Med5203Input();
        input.setData(data);
        return input;
    }

    public static Med5204Input assemble5204(QueryFee5204Command command) {
        Med5204Input.Med5204Data data = new Med5204Input.Med5204Data()
            .setPsnNo(command.getPsnNo())
            .setMdtrtId(command.getMdtrtId());
        if (isNotEmpty(command.getSetlId())) {
            data.setSetlId(command.getSetlId().trim());
        }
        Med5204Input input = new Med5204Input();
        input.setData(data);
        return input;
    }

    public static Med5205Input assemble5205(QueryOpspDrug5205Command command) {
        Med5205Input.Med5205Data data = new Med5205Input.Med5205Data()
            .setPsnNo(command.getPsnNo())
            .setBegntime(command.getBegntime());
        if (isNotEmpty(command.getEndtime())) {
            data.setEndtime(command.getEndtime().trim());
        }
        Med5205Input input = new Med5205Input();
        input.setData(data);
        return input;
    }

    public static Med5206Input assemble5206(QueryCum5206Command command) {
        Med5206Input.Med5206Data data = new Med5206Input.Med5206Data().setPsnNo(command.getPsnNo());
        if (isNotEmpty(command.getCumYm())) {
            data.setCumYm(command.getCumYm().trim());
        }
        Med5206Input input = new Med5206Input();
        input.setData(data);
        return input;
    }

    public static Med5301Input assemble5301(QueryOpspFiling5301Command command) {
        Med5301Input.Med5301Data data = new Med5301Input.Med5301Data().setPsnNo(command.getPsnNo());
        Med5301Input input = new Med5301Input();
        input.setData(data);
        return input;
    }
}

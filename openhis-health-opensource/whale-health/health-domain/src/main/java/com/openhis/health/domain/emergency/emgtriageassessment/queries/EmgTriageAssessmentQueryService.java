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

package com.openhis.health.domain.emergency.emgtriageassessment.queries;

import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeMewsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeRemsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeTraumaRtsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.SuggestTriageLevelCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.model.DerivedScores;
import com.openhis.health.domain.emergency.emgtriageassessment.model.ScaleInput;
import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import com.openhis.health.domain.emergency.emgtriageassessment.queries.model.SuggestedLevelPreviewModel;
import com.openhis.health.domain.emergency.emgtriageassessment.service.EmgTriageAssessmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 分诊评估读侧预览（DM-EMG-003 §6.2.1）。
 */
@Service
@RequiredArgsConstructor
public class EmgTriageAssessmentQueryService {

    private final EmgTriageAssessmentPolicy emgTriageAssessmentPolicy;

    public SuggestedLevelPreviewModel previewSuggestedLevel(PreviewSuggestedLevelQuery query) {
        if (query == null) {
            query = new PreviewSuggestedLevelQuery();
        }
        VitalsInput vitals = query.getVitalsInput();
        ScaleInput scale = query.getScaleInput();

        ComputeMewsCommand mewsCmd = new ComputeMewsCommand();
        mewsCmd.setVitalsInput(vitals);
        mewsCmd.setMewsDisabledByOrganization(query.getMewsDisabledByOrganization());
        Integer mews = emgTriageAssessmentPolicy.computeMews(mewsCmd);

        ComputeRemsCommand remsCmd = new ComputeRemsCommand();
        remsCmd.setPatientAgeYears(query.getPatientAgeYears());
        remsCmd.setGcs(scale != null ? scale.getGcs() : null);
        if (vitals != null) {
            int pulse = parseInt(vitals.getPulse());
            int sbp = parseInt(vitals.getBpSystolic());
            int dbp = parseInt(vitals.getBpDiastolic());
            int rr = parseInt(vitals.getRespiration());
            int spo2 = parseInt(vitals.getSpo2());
            remsCmd.setPulse(pulse >= 0 ? pulse : null);
            remsCmd.setSystolicBp(sbp >= 0 ? sbp : null);
            remsCmd.setDiastolicBp(dbp >= 0 ? dbp : null);
            remsCmd.setRespiratoryRate(rr >= 0 ? rr : null);
            remsCmd.setSpo2(spo2 >= 0 ? spo2 : null);
        }
        Optional<BigDecimal> remsOpt = emgTriageAssessmentPolicy.computeRems(remsCmd);

        DerivedScores scores = new DerivedScores();
        scores.setMewsScore(mews);
        if (remsOpt.isPresent()) {
            scores.setRemsScore(remsOpt.get().doubleValue());
        }

        ComputeTraumaRtsCommand rtsCmd = new ComputeTraumaRtsCommand();
        if (scale != null && vitals != null) {
            int gcsVal = scale.getGcs() != null ? scale.getGcs() : -1;
            int sbpVal = parseInt(vitals.getBpSystolic());
            int rrVal = parseInt(vitals.getRespiration());
            if (gcsVal >= 0 && sbpVal >= 0 && rrVal >= 0) {
                rtsCmd.setGcs(gcsVal);
                rtsCmd.setSbp(sbpVal);
                rtsCmd.setRr(rrVal);
                BigDecimal rts = emgTriageAssessmentPolicy.computeTraumaRts(rtsCmd);
                if (rts != null) {
                    scores.setTraumaRts(rts);
                }
            }
        }

        SuggestTriageLevelCommand suggestCmd = new SuggestTriageLevelCommand();
        suggestCmd.setDerivedScores(scores);
        suggestCmd.setVitalsInput(vitals);
        suggestCmd.setScaleInput(scale);
        suggestCmd.setPatientProfileKey(query.getPatientProfileKey());
        Integer level = emgTriageAssessmentPolicy.suggestTriageLevel(suggestCmd);

        SuggestedLevelPreviewModel model = new SuggestedLevelPreviewModel();
        model.setSuggestedLevel(level);
        model.setMewsScore(mews);
        model.setRemsScore(scores.getRemsScore());
        if (scores.getTraumaRts() != null) {
            model.setRtsScore(scores.getTraumaRts().doubleValue());
        }
        return model;
    }

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("-?\\d+").matcher(raw);
            if (!m.find()) {
                return -1;
            }
            return Integer.parseInt(m.group());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}

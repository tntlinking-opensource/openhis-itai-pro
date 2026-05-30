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

package com.openhis.health.domain.emergency.emgtriageassessment.service;

import com.openhis.health.domain.emergency.emgtriageassessment.AssessmentTypeCodes;
import com.openhis.health.domain.emergency.emgtriageassessment.command.BuildAssessmentPayloadCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeMewsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeRemsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.ComputeTraumaRtsCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.command.SuggestTriageLevelCommand;
import com.openhis.health.domain.emergency.emgtriageassessment.model.DerivedScores;
import com.openhis.health.domain.emergency.emgtriageassessment.model.EmgTriageAssessmentPayloadItem;
import com.openhis.health.domain.emergency.emgtriageassessment.model.ScaleInput;
import com.openhis.health.domain.emergency.emgtriageassessment.model.TriageAssessmentFormLineDto;
import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分诊评估计算策略（DM-EMG-003 §6.1）；无持久化、无领域事件。
 *
 * <p>ESI/ERTA/TRAUMA 综合全自动映射尚未在 DM-EMG-003 §9.2 完全落地时，{@link #buildAssessmentPayload} 仅输出表单给定类型，
 * 分级短路以 DM §9.1 已落地子集为准。</p>
 */
@Service
public class EmgTriageAssessmentPolicy {

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * MEWS：机构关闭或未达到「可计算」门槛时返回 {@code null}（BR-EMG-ASSESS-002 / SDD §9.1）。
     */
    public Integer computeMews(ComputeMewsCommand cmd) {
        if (cmd == null || Boolean.TRUE.equals(cmd.getMewsDisabledByOrganization())) {
            return null;
        }
        VitalsInput v = cmd.getVitalsInput();
        if (v == null) {
            return null;
        }
        String avpu = resolveAvpu(cmd.getAvpuCode(), v.getMewsAvpu());
        if (!hasMewsMinimumSignals(v, avpu)) {
            return null;
        }
        double rr = firstDoubleOrZero(v.getRespiration());
        double hr = firstDoubleOrZero(v.getPulse());
        double sbp = firstDoubleOrZero(v.getBpSystolic());
        double temp = firstDoubleOrZero(v.getTemperature());
        int avpuPts = avpuPoints(avpu);
        int rrPts = mewsRespiratoryPoints(rr);
        int hrPts = mewsHeartRatePoints(hr);
        int sbpPts = mewsSystolicPoints(sbp);
        int tempPts = mewsTemperaturePoints(temp);
        int sum = rrPts + hrPts + sbpPts + tempPts + avpuPts;
        if (sum < 0) {
            sum = 0;
        }
        if (sum > 14) {
            sum = 14;
        }
        return sum;
    }

    /**
     * 创伤 RTS；输入不足时不产出 {@code null}（BR-EMG-ASSESS-003）。
     */
    public BigDecimal computeTraumaRts(ComputeTraumaRtsCommand cmd) {
        if (cmd == null || cmd.getGcs() == null || cmd.getSbp() == null || cmd.getRr() == null) {
            return null;
        }
        int gcsCode = rtsGcsCode(cmd.getGcs());
        int sbpCode = rtsSbpCode(cmd.getSbp());
        int rrCode = rtsRrCode(cmd.getRr());
        double raw = 0.9368 * gcsCode + 0.7326 * sbpCode + 0.2908 * rrCode;
        return BigDecimal.valueOf(raw).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * REMS：年龄或 GCS 缺失则不产出（DM §6.1.3）。
     */
    public Optional<BigDecimal> computeRems(ComputeRemsCommand cmd) {
        if (cmd == null || cmd.getPatientAgeYears() == null || cmd.getGcs() == null) {
            return Optional.empty();
        }
        if (cmd.getPulse() == null
                || cmd.getSystolicBp() == null
                || cmd.getDiastolicBp() == null
                || cmd.getRespiratoryRate() == null
                || cmd.getSpo2() == null) {
            return Optional.empty();
        }
        double map = (cmd.getSystolicBp() + 2.0 * cmd.getDiastolicBp()) / 3.0;
        // SDD 入参未承载体温分项时，不计温度扣分（与 DM §9.4 REMS 分项预留一致）。
        double total = remsAgePoints(cmd.getPatientAgeYears())
                + remsMapPoints(map)
                + remsHrPoints(cmd.getPulse())
                + remsRrPoints(cmd.getRespiratoryRate())
                + remsSpo2Points(cmd.getSpo2())
                + remsGcsPoints(cmd.getGcs());
        return Optional.of(BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 建议分级：DM-EMG-003 §9.1 短路（末条默认 IV 级）；不含完整 ESI/ERTA Gap。
     */
    public Integer suggestTriageLevel(SuggestTriageLevelCommand cmd) {
        DerivedScores ds = cmd != null ? cmd.getDerivedScores() : null;
        VitalsInput v = cmd != null ? cmd.getVitalsInput() : null;
        ScaleInput s = cmd != null ? cmd.getScaleInput() : null;

        Integer gcs = s != null ? s.getGcs() : null;
        Integer pain = s != null ? s.getPainNrs() : null;
        int pulse = parseIntFlexible(v != null ? v.getPulse() : null);
        int spo2 = parseIntFlexible(v != null ? v.getSpo2() : null);
        Double rems = ds != null ? ds.getRemsScore() : null;
        Integer mews = ds != null ? ds.getMewsScore() : null;

        if (gcs != null && gcs <= 8) {
            return 1;
        }
        if (pain != null && pain >= 8) {
            return 2;
        }
        if (pain != null && pain >= 5) {
            return 3;
        }
        if (spo2 >= 0 && spo2 < 92) {
            return 2;
        }
        if (pulse >= 0 && (pulse > 120 || pulse < 50)) {
            return 3;
        }
        if (rems != null && rems >= 7.0) {
            return 2;
        }
        if (mews != null && mews >= 5) {
            return 2;
        }
        return 4;
    }

    public List<EmgTriageAssessmentPayloadItem> buildAssessmentPayload(BuildAssessmentPayloadCommand cmd) {
        if (cmd == null || cmd.getEmgTriageId() == null || cmd.getFormDto() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "组装载荷缺少主键或表单");
        }
        LocalDateTime assessedAt = cmd.getAssessedAt() != null ? cmd.getAssessedAt() : LocalDateTime.now();
        List<EmgTriageAssessmentPayloadItem> rows = new ArrayList<>();
        for (TriageAssessmentFormLineDto line : cmd.getFormDto().getLines()) {
            if (line == null || StringUtils.isBlank(line.getAssessmentType())) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "评估类型不能为空");
            }
            if (!AssessmentTypeCodes.isAllowed(line.getAssessmentType())) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_RULE_CONFLICT, "assessment_type 不在 DM-EMG-001 §4.5 白名单");
            }
            if (StringUtils.isBlank(line.getAssessmentDataJson())) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "assessment_data 不能为空");
            }
            EmgTriageAssessmentPayloadItem row = new EmgTriageAssessmentPayloadItem();
            row.setEmgTriageId(cmd.getEmgTriageId());
            row.setAssessmentType(line.getAssessmentType());
            row.setAssessmentData(line.getAssessmentDataJson());
            row.setAssessmentScore(line.getAssessmentScore());
            row.setAssessedAt(assessedAt);
            row.setAssessedBy(cmd.getAssessedBy());
            rows.add(row);
        }
        return rows;
    }

    private static boolean hasMewsMinimumSignals(VitalsInput v, String avpuRaw) {
        boolean text =
                StringUtils.isNotBlank(v.getRespiration())
                        || StringUtils.isNotBlank(v.getPulse())
                        || StringUtils.isNotBlank(v.getBpSystolic())
                        || StringUtils.isNotBlank(v.getTemperature());
        boolean avpu = StringUtils.isNotBlank(avpuRaw);
        return text || avpu;
    }

    private static String resolveAvpu(String primary, String fallback) {
        if (StringUtils.isNotBlank(primary)) {
            return primary.trim();
        }
        if (fallback == null) {
            return "";
        }
        return fallback.trim();
    }

    private static int avpuPoints(String avpuRaw) {
        if (StringUtils.isBlank(avpuRaw)) {
            return 0;
        }
        String u = avpuRaw.trim().toUpperCase(Locale.ROOT);
        char c = u.charAt(0);
        if (c == 'U' || u.contains("UNRESP")) {
            return 3;
        }
        if (c == 'P') {
            return 2;
        }
        if (c == 'V') {
            return 1;
        }
        if (c == 'A') {
            return 0;
        }
        return 0;
    }

    private static int mewsRespiratoryPoints(double rr) {
        if (rr <= 0) {
            return 0;
        }
        if (rr < 9) {
            return 2;
        }
        if (rr <= 14) {
            return 0;
        }
        if (rr <= 20) {
            return 1;
        }
        if (rr <= 29) {
            return 2;
        }
        return 3;
    }

    private static int mewsHeartRatePoints(double hr) {
        if (hr <= 0) {
            return 0;
        }
        if (hr <= 40) {
            return 2;
        }
        if (hr <= 50) {
            return 1;
        }
        if (hr <= 100) {
            return 0;
        }
        if (hr <= 110) {
            return 1;
        }
        if (hr <= 129) {
            return 2;
        }
        return 3;
    }

    private static int mewsSystolicPoints(double sbp) {
        if (sbp <= 0) {
            return 0;
        }
        if (sbp <= 70) {
            return 3;
        }
        if (sbp <= 80) {
            return 2;
        }
        if (sbp <= 100) {
            return 1;
        }
        if (sbp <= 199) {
            return 0;
        }
        return 2;
    }

    private static int mewsTemperaturePoints(double tempC) {
        if (tempC <= 0) {
            return 0;
        }
        if (tempC <= 35.0) {
            return 2;
        }
        if (tempC <= 36.0) {
            return 1;
        }
        if (tempC <= 38.0) {
            return 0;
        }
        if (tempC <= 39.0) {
            return 1;
        }
        return 2;
    }

    private static double firstDoubleOrZero(String raw) {
        Optional<Double> v = firstNumber(raw);
        if (v.isEmpty()) {
            return 0d;
        }
        return v.get();
    }

    private static Optional<Double> firstNumber(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Optional.empty();
        }
        Matcher m = FIRST_NUMBER.matcher(raw);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(Double.parseDouble(m.group()));
    }

    private static int parseIntFlexible(String raw) {
        Optional<Double> v = firstNumber(raw);
        if (v.isEmpty()) {
            return -1;
        }
        return (int) Math.round(v.get());
    }

    private static int rtsGcsCode(int gcs) {
        if (gcs >= 13) {
            return 4;
        }
        if (gcs >= 9) {
            return 3;
        }
        if (gcs >= 6) {
            return 2;
        }
        if (gcs >= 4) {
            return 1;
        }
        return 0;
    }

    private static int rtsSbpCode(int sbp) {
        if (sbp > 89) {
            return 4;
        }
        if (sbp >= 76) {
            return 3;
        }
        if (sbp >= 50) {
            return 2;
        }
        if (sbp >= 1) {
            return 1;
        }
        return 0;
    }

    private static int rtsRrCode(int rr) {
        if (rr >= 10 && rr <= 29) {
            return 4;
        }
        if (rr > 29) {
            return 3;
        }
        if (rr >= 6) {
            return 2;
        }
        if (rr >= 1) {
            return 1;
        }
        return 0;
    }

    private static double remsAgePoints(int age) {
        if (age <= 44) {
            return 0;
        }
        if (age <= 54) {
            return 2;
        }
        if (age <= 64) {
            return 3;
        }
        return 4;
    }

    private static double remsMapPoints(double map) {
        if (map >= 70) {
            return 0;
        }
        if (map >= 65) {
            return 2;
        }
        if (map >= 55) {
            return 3;
        }
        if (map >= 40) {
            return 5;
        }
        return 6;
    }

    private static double remsHrPoints(int hr) {
        if (hr <= 39) {
            return 0;
        }
        if (hr <= 109) {
            return 1;
        }
        if (hr <= 129) {
            return 2;
        }
        return 3;
    }

    private static double remsRrPoints(int rr) {
        if (rr <= 8 || rr >= 29) {
            return 3;
        }
        return 0;
    }

    private static double remsSpo2Points(int spo2) {
        if (spo2 >= 89) {
            return 0;
        }
        if (spo2 >= 86) {
            return 2;
        }
        return 4;
    }

    private static double remsGcsPoints(int gcs) {
        if (gcs >= 15) {
            return 0;
        }
        if (gcs >= 13) {
            return 3;
        }
        return 6;
    }
}

# AS-CMP-ARCH-001 档案共通 · SDD

> **包**：`com.zxplat.plugin.campus.application.archive.query` · **类**：`CampusArchiveQueryAppService`

同时作为 `CampusPublicExpenseEligibilityReader` / `CampusArchiveQueryReader` 的实现委托目标。

资格校验伪代码：

```
if !masterSwitch → SWITCH_OFF
resolve subject + pe_profile by patientId
if gf_status == NOT_ELIGIBLE → GF_STATUS_DENY
if !eligible_flag → PROFILE_DISABLED
if student → school/exam/college/campus
if staff → job/dept/campus/(medical expiry if present)/patient
return EligibilityView(+ cardNo, gfStatusEnum)
```

HIS：`syncPeProfileFromHis` → Domain#syncFromHis

| v1.1 | 2026-07-19 | card_no / gf_status / syncHis |
| v1.0 | 2026-07-19 | PASS |

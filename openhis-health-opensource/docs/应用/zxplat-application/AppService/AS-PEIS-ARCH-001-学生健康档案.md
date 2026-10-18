# 学生健康档案 · 应用服务设计（AS）

> **编码**：AS-PEIS-ARCH-001 · **版本**：v1.1 · **日期**：2026-07-25  
> **边界**：BAS-05 健康档案读写；学籍扩展只读 Campus；历年体检查询；档案完整性提示；**不**单独强依赖菜单（诊台/登记内嵌 + 轻页「体检历史」）  
> **关联**：BR-PE-ANNUAL-01（自然年唯一年度体检）；REP-05 毕业历年合打；校医院新生/年检/教职工

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 读写通用健康摘要；拼装 Campus 学生/教职工扩展；列历年 PE；完整性提示 |
| **在外** | Campus 学籍主维护；总检结论写 DiagnosticReport；收费/建单 |
| **存储** | `peis_patient_health_archive`；历年事实 = `adm_encounter(class=PE)` + `peis_encounter_ext` |

## 2. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PE-ARCH-01** | 健康档案以 `patientId` 为主键；学生场景须能解析学号（Campus 优先，团检名册兜底） |
| **BR-PE-ANNUAL-01** | 同一患者同一**自然年**仅允许一条未取消的年度体检 `Encounter(class=PE)` |
| **BR-PE-ARCH-02** | 体检类型 `examKind`：`freshman` / `annual` / `staff` / `other`，写入 `package_snapshot_json` |
| **BR-PE-ARCH-03** | 档案完整性：无 `block` 级缺失视为 complete；毕业场景缺既往史/定稿总检为 block |

### 2.1 完整性判定（校医院）

| 项 | 级别 | 条件 |
|----|------|------|
| 既往史未填 | 在校 warn / 毕业 block | `pastHistory` 空 |
| 过敏史未填 | warn | `allergySummary` 空 |
| 未见新生检 | info / 毕业 warn | 无 `examKind=freshman` |
| 当年未年检 | warn（在校） | 本自然年无 PE |
| 无定稿总检 | block（毕业） | 无 DR.final |
| 无任何体检 | warn / 毕业 block | 历史行为空 |

## 3. 接口

| IF | 动作 | AppService | 读写 |
|----|------|------------|:--:|
| **IF-PEIS-ARCH-01** | 加载健康档案（可选附带完整性） | `peisHealthArchive/loadArchive` | 读 |
| **IF-PEIS-ARCH-02** | 保存通用既往史/习惯 | `peisHealthArchive/saveArchive` | 写 |
| **IF-PEIS-ARCH-03** | 档案完整性检查 | `peisHealthArchive/checkArchiveCompleteness` | 读 |
| **IF-PEIS-HIST-01** | 学生/教职工历年体检 | `peisExamHistory/listPatientExamHistory` | 读 |

`listPatientExamHistory` 入参：`patientId` **或** `studentNo` **或** `employeeNo`；可选 `examKinds`、`onlyFinal`。

## 4. 关联

- Campus `PeisCampusIdentityPort`（SPI，Campus 启用时装配）  
- AS-PEIS-REG-001 登记写入 `examKind`  
- AS-PEIS-REP-001 · REP-05 复用 HIST-01  

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-25 | 历年历史 + 完整性 + examKind |
| v1.0 | 2026-07-19 | 首版 BAS-05 产品化 |

# GAP-CLN-001 药房核发无皮试 Gate

> **问题类型**：临床规则与主数据
> **严重度**：**高危**（医疗安全）
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-012](../07-临床规则与主数据/SYS-IMP-012-临床闭环Gate与主数据锁价.md)
> **关联 IF/BR**：IF-IP-STK-03；BR-IP-STK-006/009；BR-ORD-025 皮试联动

---

## 1. 问题描述

皮试阳性/未完成仍核发（药房无 Gate）

## 2. 发生场景

`OutpatientPharmacyDispenseAppService` / `InpatientPharmacyDispenseAppService` 的 `validateDispensable` **未**检索 `MedicationRequest.skinTestFlag` 与皮试结果（阳性/阴性/未测）。

## 3. 根因分析

皮试模块与药房模块未编排；AS 门诊 UI Gap 加剧「未登记仍发药」。

## 4. 业务影响

皮试药未测或阳性仍出库。

## 5. 修改方案

1. 新增 **`SkinTestDispenseGatesupport**：
   - `skinTestFlag=true` → 须存在有效皮试记录且非阳性；
   - 阳性 → `PHA-STK-403` 拒绝核发（需 pharmacist override role 可选）。
2. 接入：`prepareMedicationDispense`、`dispenseMedication` 前。
3. 与护士站皮试 `NurseStationSkinTestAppService` 读模型对齐。


## 6. 涉及代码

- `OutpatientPharmacyDispenseAppService.java`
- `InpatientPharmacyDispenseAppService.java`
- 新建 `SkinTestDispenseGateSupport.java`

## 7. 验收用例

- T1：skinTest 药无皮试记录 → 核发失败。
- T2：皮试阳性 → 核发失败。
- T3：皮试阴性 → 核发成功。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |

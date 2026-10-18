# health.integration.workinjury

HIS SPI 的吉林工伤 bridge 实现（`com.zxplat.plugin.wi.bridge.integration.workinjury`）。

## JavaDoc 约定

与 CHS `health.integration.insurance.*` 对齐：

- 类注释：**SPI** + **Health 调用方** + **Bridge 委托**
- 每个 `@Override` 一行（含 infno / 场景）

## Extension 对照

| 类 | SPI | Health 调用方 | 委托 |
|----|-----|---------------|------|
| `WorkInjuryInvokeExtensionImpl` | Win invoke 循环 | 嵌入门诊流程 / `WorkInjuryInvokeAppService` | domain invoke access Invoker |
| `WorkInjuryRegisterExtensionImpl` | 9102/2301 登记 | `OutpatientRegistrationAppService` | `WorkInjuryRegisterOrchestrator` |
| `WorkInjuryOutpatientSettlementExtensionImpl` | 门诊工伤收银 | `OutpatientChargeAppService` | `WorkInjuryOutpatientSettleOrchestrator` |
| `WorkInjuryReconciliationExtensionImpl` | 1320–1322 对账 | `ChsReconciliationAppService` | `WorkInjuryInvokeOrchestrator` |
| `WorkInjuryApprovalUploadExtensionImpl` | 2301 审批上传 | 挂号 / 医生站 | `WorkInjuryInvokeOrchestrator` |
| `WorkInjuryEncounterContextExtensionImpl` | 就诊上下文 | 医生站 / 列表 | `yb_wi_encounter_anchor` |
| `WorkInjuryModuleExtensionImpl` | 模块能力 | capability API | `WorkInjuryContractResolver` |
| `WorkInjuryIntegrationMapper` | Extension DTO ↔ domain | — | 无 IO |

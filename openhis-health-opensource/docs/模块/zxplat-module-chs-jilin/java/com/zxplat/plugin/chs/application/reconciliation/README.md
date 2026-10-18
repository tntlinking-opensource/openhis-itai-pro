# reconciliation 应用层

医保对账/清算 UI（3201、3202、3203A–3209A）。

**文档**：`docs/模块/zxplat-module-chs-jilin/AppService/AS-CHS-REC-001-对账清算中心.md` · `AppService-SDD/AS-CHS-REC-001-对账清算中心-SDD.md`

## 包结构

```text
reconciliation/
  service/InsuranceReconciliationAppService   # 薄入口，直调 domain access 端口
  request/     ListLocalReconRecordsRequest
  response/    ListLocal* / Local* Response
  mapper/      InsuranceReconciliationAppMapper
```

## infno 索引

| infno | 落点 |
|-------|------|
| 3201 | domain `Recon3201Invoker`（bridge `Recon3201Invoker` 实现） |
| 3202 | domain `Recon3202DetailInvoker`（bridge `Recon3202Workflow` 实现） |
| 3203A–3209A | domain `Recon3203AInvoker`–`Recon3209AInvoker` |

**P12**：Invoker 已迁至 bridge `reconciliation/`；本包禁止 `com.zxplat.plugin.chs.gateway.*` import（ArchTest 约束）。

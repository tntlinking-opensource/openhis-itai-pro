# medicalinfo 应用层

医保信息查询（5201–5306、5301）。

**文档**：`docs/.../AS-CHS-MED-001` · `AppService-SDD/AS-CHS-MED-001-医保信息查询-SDD.md`

## 包结构

```text
medicalinfo/
  service/InsuranceMedicalInfoQueryAppService   # 薄入口，直调 domain access 端口
```

## infno 索引

| infno | 落点 |
|-------|------|
| 5201–5301 | domain `Med5201QueryInvoker`–`Med5301QueryInvoker`（bridge `Med5201Invoker`–`Med5301Invoker` 实现） |

同页多 query，无 Workflow 层：`AppService` → domain access 端口。

**P11**：Invoker 已迁至 bridge `medicalinfo/invoker/`；本包禁止 `com.zxplat.plugin.chs.gateway.*` import（ArchTest 约束）。

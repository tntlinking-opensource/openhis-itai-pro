# platformdict 应用层

平台字典 1901 同步。

## 包结构

```text
platformdict/
  service/InsurancePlatformDictAppService
  invoker/     Dict1901PageInvoker
  support/     Dict1901SyncWorkflow、DictPlatformSupport
```

## infno 索引

| infno | Invoker | Workflow |
|-------|---------|----------|
| 1901 | `Dict1901PageInvoker` | `Dict1901SyncWorkflow`（分页拉取 + 本地 upsert） |

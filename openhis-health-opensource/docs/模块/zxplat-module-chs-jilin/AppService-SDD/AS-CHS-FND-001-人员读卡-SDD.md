# 人员读卡 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-FND-001-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-FND-001

## 1. 边界

| AppService | HTTP | infno |
| --- | --- | --- |
| `InsurancePersonAppService` | `/api/app/insurancePerson` | 1101 |

## 2. 编排

| 方法 | 落点 |
| --- | --- |
| `getPersonInfo` | `InsurancePersonAppMapper` → domain `Op1101QueryInvoker` |

## 3. 包结构

```text
person/
  service/InsurancePersonAppService.java
  request/YbPersonInfoRequest.java
  mapper/InsurancePersonAppMapper.java
```

包路径：`com.zxplat.plugin.chs.application.foundation.person.*`

## 4. 事务

只读查询；1101 写缓存若存在则在 domain/invoker 内局部事务。

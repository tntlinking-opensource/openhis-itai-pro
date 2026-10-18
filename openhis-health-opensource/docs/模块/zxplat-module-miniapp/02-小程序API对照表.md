# 02 · 小程序 API 对照表（团检 + 鉴权联调契约）

> **文档性质**：前端联调契约（与成码 IF 对齐）  
> **前缀**：`POST /api/miniapp/{appService}/{method}`  
> **鉴权**：除登录外须 `Authorization: Bearer <token>` 或 `X-Miniapp-Token: <token>`  
> **版本**：v0.1 | **日期**：2026-08-28 | **状态**：骨架可用

---

## 1. 通用约定

| 项 | 说明 |
|----|------|
| Content-Type | `application/json` |
| 成功包体 | `{ "success": true, "data": ... }`（以项目 `Result` 为准） |
| 未登录 | HTTP 401 · `UNAUTHORIZED` |
| 须绑定就诊人 | `BIND_REQUIRED` |
| 租户 | 由网关/院端会话注入；联调走现有租户头 |

**推荐联调顺序**

```text
devLogin / autoLogin
  → bindCampusStudent | bindCampusStaff | bindPatient
  → miniappPeis.lookupRosterForDesk
  → miniappPeis.selectRosterPackage
  →（到检前）miniappPeis.changeRosterSelectedPackage
```

---

## 2. miniappAuth（身份）

基路径：`/api/miniapp/miniappAuth`

### 2.1 autoLogin（匿名）

```http
POST /api/miniapp/miniappAuth/autoLogin
```

请求：

```json
{ "code": "wx.login返回的code", "appId": "可选，缺省用配置" }
```

开发：

- 未配 `wechat-app-secret` 且 `mock-login-enabled=true`：`code` 可当 openid，或 `mock:oXXXX`
- 已配 secret：走微信 `jscode2session`；仍可用 `mock:oXXXX`（mock 开启时）

响应 `data`：

| 字段 | 说明 |
|------|------|
| token | 会话令牌（后续请求头携带） |
| accountId / sessionId | 账号与会话 |
| patientId / practitionerId | 当前选中（可空） |
| bindRequired | true=须引导绑定就诊人 |
| openid / appId | 展示用 |

### 2.2 devLogin（匿名 · 仅 mock）

```json
{ "openid": "dev-openid-001", "appId": "可选" }
```

### 2.3 currentSession / logout

- `currentSession`：无 body  
- `logout`：撤销当前会话

### 2.4 绑定就诊人

| 方法 | body | 说明 |
|------|------|------|
| `bindPatient` | `{ patientId, asDefault? }` | 已有 Patient 主键（测试） |
| `bindCampusStudent` | `{ studentNo, name, asDefault? }` | Campus 学号+姓名 |
| `bindCampusStaff` | `{ employeeNo, name, asDefault? }` | Campus 工号+姓名 |
| `switchPatient` | `{ patientId }` | 须已绑定 |

### 2.5 医护

| 方法 | body |
|------|------|
| `bindPractitioner` | `{ practitionerId?, employeeNo?, name?, asDefault? }` — `practitionerId` 与 `employeeNo` 二选一；按工号绑定时 `name` 可选（有则校验） |
| `switchPractitioner` | `{ practitionerId }` |

---

## 3. miniappPeis（团检选定）

基路径：`/api/miniapp/miniappPeis`  
**须登录且会话有 patientId**（C1）。

### 3.1 lookupRosterForDesk

```json
{ "patientId": null }
```

`patientId` 可省略，取会话当前就诊人。

响应 `data.matches[]`（节选）：

| 字段 | 说明 |
|------|------|
| rosterId / batchId / batchName | 名册与分组 |
| companyName / contractId | 单位与合同 |
| optionalPackages[] | `{ packageActivityId, packageName }` |
| selectedPackageActivityId / packageSelected | 选定态 |
| enrolled / arrived / canChangePackage | 建单/到检/可否换套餐 |
| encounterId | 已建单则有 |

### 3.2 selectRosterPackage（选定即建单）

```json
{ "rosterId": "...", "packageActivityId": "...", "orgId": null }
```

响应：`rosterId / encounterId / busNo / selectedPackageActivityId`

### 3.3 changeRosterSelectedPackage（到检前）

```json
{ "rosterId": "...", "packageActivityId": "..." }
```

已到检 → 业务拒绝；流水号不变。

---

## 4. 配置（后端）

```yaml
zxplat.modules.miniapp.enabled: true
zxplat.miniapp.default-app-id: <小程序appId>
zxplat.miniapp.wechat-app-secret: <正式环境必填>
zxplat.miniapp.mock-login-enabled: true   # 开发 true；生产建议 false
zxplat.miniapp.session-ttl-hours: 72
zxplat.miniapp.system-operator-practitioner-id: <系统操作人>
zxplat.miniapp.gateway-api-key: <与DMZ网关一致；留空则不校验直连>
```

内网 BFF 配置 `gateway-api-key` 后，所有 `/api/miniapp/**` 须携带 `X-Miniapp-Gateway-Key`（DMZ 网关自动附加；本地直连需手动加 Header 或留空该配置）。

启动：`-Pwith-miniapp`（并启用 Campus 以支持校园绑定）。

**DMZ 网关（remote）**

```powershell
cd D:\0.product\02.Whale\zxplat
mvn package -pl zxplat-module-miniapp/module-miniapp-gateway-starter -am -DskipTests
java -jar module-miniapp-gateway-starter/target/module-miniapp-gateway-starter-*-gateway.jar
```

配置 `zxplat.miniapp.gateway.remote-base-url` 指向内网 `health-app`（已装 `module-miniapp-starter`）；可选 `api-key` 校验 `X-Miniapp-Gateway-Key`。

---

## 5. 错误码（常用）

| code | 含义 |
|------|------|
| GATEWAY_FORBIDDEN | 未带网关 Key 直连内网 BFF |
| UNAUTHORIZED | 未登录/令牌失效 |
| BIND_REQUIRED | 须绑定就诊人 |
| CAMPUS_DISABLED | Campus 未启用 |
| CAMPUS_STUDENT_NOT_FOUND / CAMPUS_STAFF_NOT_FOUND | 学号/工号不存在 |
| CAMPUS_NAME_MISMATCH | 姓名不一致 |
| PRACTITIONER_NOT_FOUND | 工号未找到医护 |
| PRACTITIONER_NAME_MISMATCH | 医护姓名与档案不一致 |
| CAMPUS_PATIENT_MISSING | 校园档未绑 Patient |
| WECHAT_CODE2SESSION_FAILED | 微信登录失败 |
| PACKAGE_NOT_SELECTED / ROSTER_PATIENT_MISMATCH 等 | 团检业务（PEIS） |

---

## 6. 开发种子（health-dev / campus V4）

Flyway 启用 dev profile 后自动加载；用于 H5 联调闭环。

| 场景 | 账号 | 说明 |
|------|------|------|
| 学生绑定 | 学号 `DEV2026001` + 姓名 `李先生` | Campus V4 → patient `325580322788872192` |
| 教职工绑定 | 工号 `DEV-E001` + 姓名 `小王` | Campus V4 → patient `325866857001652224` |
| 医护绑定 | 工号 `PR0002` + 姓名 `门诊医生`（可选） | V26 更新 `adm_practitioner.bus_no` |
| 团检选定 | 绑定李先生后 `lookupRosterForDesk` | 名册 `920000000000000401`，可选套餐 A/B |
| 系统操作人 | `308791351298756608`（admin / PR0001） | `zxplat.miniapp.system-operator-practitioner-id` 默认值 |

小程序 `.env.development` 建议：

```env
VITE_API_BASE=http://127.0.0.1:8081
VITE_TENANT_ID=1
VITE_DEV_OPENID=dev-openid-001
```

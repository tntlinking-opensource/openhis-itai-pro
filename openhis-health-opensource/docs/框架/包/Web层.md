# 包 · web（Web 层）

> **源码包**：`com.zxplat.framework.web`  ·  目录：`zxplat-framework/src/main/java/com/zxplat/framework/web/`

> Auto API 注册、OpenAPI、前端应用配置聚合。

## Overview

| 项 | 值 |
|----|-----|
| 制品 | `com.zxplat:zxplat-framework`（包 web） |
| 包根 | `com.zxplat.framework.web` |
| AutoConfiguration | `ZxplatFrameworkAutoConfiguration` |
| 同 jar 包 | 同制品内的 core、identity、permission、menu、setting、job |

## Key Types

| 类型 | 说明 |
|------|------|
| `AppServiceControllerRegistrar` | 扫描 `*AppService` 并注册 `POST /api/app/**` |
| `ApplicationConfigurationAppService` | 实现 `IApplicationConfigurationAppService` |
| `OpenApiConfig` / `DynamicOpenApiCustomizer` | Swagger 文档 |

> 扩展模块未装配时的友好错误见包 **core** 的 `ExtensionModuleAppServiceRegistry`（不在 web）。

## AppServices · ApplicationConfigurationAppService

| 方法 | 说明 |
|------|------|
| `get()` | 聚合当前用户的前端配置（权限、用户、租户、设置等） |
| `get(userId, tenantId)` | 登录前/无 SecurityContext 时按显式用户租户构建 |
| `applyImpersonatorFields(...)` | 注入代理登录 impersonator 字段 |

登录成功响应中的 `application` 字段即此 DTO，供前端初始化权限与菜单。

## Auto API 机制摘要

详见 [自动接口.md](../自动接口.md)：

1. 启动后懒加载扫描所有 `@Service` 且类名以 `AppService` 结尾的 Bean。
2. 每个 public 实例方法 → `POST /api/app/{service}/{method}`。
3. 请求体为 JSON 数组，返回值包装 `Result<T>`。

## Configuration

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

app:
  logging:
    api-params-enabled: true
    api-params-max-length: 2000
```

## Extension Points

- 各业务模块新增 `XxxAppService` 即可自动暴露 API，无需改 `web` 包代码。

## Related

- [自动接口.md](../自动接口.md)
- [身份认证.md](./身份认证.md)
- [核心.md](./核心.md)

---

**上一篇**：[运行时.md](./运行时.md) · **下一篇**：[多租户.md](../主题/多租户.md)

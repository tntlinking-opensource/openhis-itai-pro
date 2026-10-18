import type { UserConfig } from 'vite';

/** 仅预构建 npm 依赖；workspace 包走 B/C 启动链瘦身，避免预构建与 Vue 实例冲突 */
const OPTIMIZE_DEPS_INCLUDE = [
  '@zxplat/common-ui/es/authentication',
  '@zxplat/common-ui/es/loading',
  '@zxplat/access/es/directive',
  '@zxplat/hooks/es/use-design-tokens',
  '@zxplat/layouts/es/authentication',
  '@vueuse/core',
  'dayjs',
  'element-plus',
  'pinia',
  'vue',
  'vue-router',
  'zod',
] as const;

/**
 * 开发环境性能：预构建 workspace 依赖 + 收窄 warmup，减少 @fs 千级请求。
 * 保留 Element Plus useSource（scss 源码），不改为全量 CSS。
 */
export const devPerformanceConfig: Pick<UserConfig, 'optimizeDeps' | 'server'> = {
  optimizeDeps: {
    include: [...OPTIMIZE_DEPS_INCLUDE],
  },
  server: {
    warmup: {
      clientFiles: [
        './index.html',
        './src/main.ts',
        './src/preferences.ts',
        './src/bootstrap.ts',
        './src/app.vue',
        './src/router/index.ts',
        './src/router/routes/core.ts',
        './src/router/guard.ts',
        './src/router/access-init-guard.ts',
        './src/views/_core/authentication/login.vue',
        './src/layouts/auth.vue',
        './src/locales/index.ts',
        './src/api/request.ts',
      ],
    },
  },
};

import type { PluginOption } from 'vite';

import { createRequire } from 'node:module';

import { defineConfig } from '@zxplat/vite-config';

import AutoImport from 'unplugin-auto-import/vite';
import ElementPlus from 'unplugin-element-plus/vite';

import { canvasEditorHighlightPatch } from './vite.canvas-editor-patch';
import { zxplatWinDownloadsDevPlugin } from './vite.zxplat-win-downloads';
import {
  createOpenHisProjectOverridePlugin,
  hydrateOpenHisProjectEnv,
  purgeStaleOpenHisProjectLocalEnv,
} from './vite.project-override-plugin';
import { createWebEleResolveAliases } from './vite.resolve-aliases';

const require = createRequire(import.meta.url);

const extraPlugins = [
  AutoImport({
    imports: [
      'vue',
      'vue-router',
      'pinia',
      {
        '@vueuse/core': [
          'useMouse',
          'useWindowSize',
          'useLocalStorage',
          'useSessionStorage',
          'useClipboard',
          'useDebounceFn',
          'useThrottleFn',
        ],
      },
    ],
    dts: true,
    eslintrc: {
      enabled: true,
      filepath: './.eslintrc-auto-import.json',
      globalsPropValue: true,
    },
  }),
  ElementPlus({
    format: 'esm',
    useSource: true, // 使用源码以便支持 SCSS 变量覆盖
  }),
] as PluginOption[];

export default defineConfig(async () => {
  // 院点：UTF-8 注入品牌 env；主线：清掉历史残留的 .env.*.local
  hydrateOpenHisProjectEnv(import.meta.dirname);
  purgeStaleOpenHisProjectLocalEnv(import.meta.dirname);
  const projectOverridePlugin = createOpenHisProjectOverridePlugin(import.meta.dirname);

  return {
    application: {},
    vite: {
      assetsInclude: ['**/*.md'],
      css: {
        preprocessorOptions: {
          scss: {
            // 如果需要通过 SCSS 变量覆盖，可以在这里添加
            // 但由于 Element Plus 变量可能没有 !default，我们主要通过 CSS 变量覆盖
          },
        },
      },
      plugins: [
        ...(projectOverridePlugin ? [projectOverridePlugin] : []),
        zxplatWinDownloadsDevPlugin(),
        canvasEditorHighlightPatch(),
        ...extraPlugins,
      ],
      define: {
        // sockjs-client@1.x 引用了 Node.js 的 global 变量，Vite ESM 环境中不存在
        global: 'globalThis',
      },
      resolve: {
        // monorepo 本地打包：优先走各 workspace 包的 development/src 入口，避免 stub dist（含 jiti）进浏览器包
        conditions: ['development', 'module', 'browser', 'default'],
        alias: {
          ...createWebEleResolveAliases(import.meta.dirname),
          jquery: require.resolve('jquery/dist/jquery.min.js'),
        },
      },
      optimizeDeps: {
        include: ['jquery', 'vue-plugin-hiprint'],
        exclude: ['@hufe921/canvas-editor'],
      },
      server: {
        warmup: {
          // 与 @zxplat/vite-config 一致，但排除 views 内文档类 .md，避免被当作 JS 预转换
          clientFiles: [
            './index.html',
            './src/bootstrap.ts',
            './src/{views,layouts,router,store,api,adapter}/**/*.{vue,ts,tsx,js,scss,css}',
          ],
        },
        proxy: {
          // iMEC 监护仪 API 代理（必须放在通用 /api 之前，优先匹配）
          // 前端 requestClient baseURL=/api，实际请求路径为 /api/imec/xxx
          '/api/imec': {
            changeOrigin: true,
            rewrite: (path: string) => path.replace(/^\/api/, ''),
            target: 'http://localhost:8081',
          },
          // iMEC WebSocket STOMP 代理
          '/ws': {
            changeOrigin: true,
            target: 'http://localhost:8081',
            ws: true,
          },
          // 本地 dev 打本机 Java :8081。
          // 注意：勿再单独配 `/api/health` 前缀代理——会误匹配 `/api/healthCheckup*`，
          // 导致 AppService 打到错误路径（No static resource）。健康检查在 rewrite 中特判。
          '/api': {
            changeOrigin: true,
            rewrite: (path: string) => {
              if (path === '/api/health' || path.startsWith('/api/health/')) {
                return path;
              }
              return path.replace(/^\/api/, '/api/app');
            },
            target: 'http://localhost:8081',
            ws: true,
          },
          '/uploads': {
            changeOrigin: true,
            target: 'http://localhost:8081',
          },
        },
      },
    },
  };
});

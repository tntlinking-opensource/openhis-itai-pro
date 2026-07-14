import fs from 'node:fs';
import path from 'node:path';

import type { Plugin } from 'vite';

const RESOLVE_EXTENSIONS = ['', '.ts', '.tsx', '.vue', '.js', '.mts'];
const INDEX_FILES = ['index.ts', 'index.tsx', 'index.vue', 'index.js'];

function resolveExistingFile(basePath: string): string | null {
  if (fs.existsSync(basePath)) {
    const stat = fs.statSync(basePath);
    if (stat.isFile()) {
      return basePath;
    }
    if (stat.isDirectory()) {
      for (const indexFile of INDEX_FILES) {
        const candidate = path.join(basePath, indexFile);
        if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
          return candidate;
        }
      }
    }
  }

  for (const ext of RESOLVE_EXTENSIONS) {
    const candidate = `${basePath}${ext}`;
    if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) {
      return candidate;
    }
  }

  return null;
}

/** 解析 OPENHIS_PROJECT / VITE_OPENHIS_PROJECT，支持带或不带 project- 前缀 */
export function resolveOpenHisProjectId(): string | undefined {
  const raw = (process.env.OPENHIS_PROJECT ?? process.env.VITE_OPENHIS_PROJECT ?? '').trim();
  if (!raw) {
    return undefined;
  }
  return raw.startsWith('project-') ? raw.slice('project-'.length) : raw;
}

export function getOpenHisProjectDir(webEleDir: string): string | null {
  const projectId = resolveOpenHisProjectId();
  if (!projectId) {
    return null;
  }

  const monorepoRoot = path.resolve(webEleDir, '../..');
  const projectDir = path.join(monorepoRoot, 'projects', `project-${projectId}`);
  return fs.existsSync(projectDir) ? projectDir : null;
}

function walkOverrideFiles(dir: string, relativeBase = ''): Array<{ abs: string; rel: string }> {
  if (!fs.existsSync(dir)) {
    return [];
  }

  const files: Array<{ abs: string; rel: string }> = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const rel = relativeBase ? `${relativeBase}/${entry.name}` : entry.name;
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkOverrideFiles(abs, rel));
      continue;
    }
    if (entry.isFile()) {
      files.push({ rel, abs });
    }
  }
  return files;
}

/**
 * 编译期重载：overrides/ 下存在同名 #/ 模块时优先使用。
 * 纯数据/无外部依赖的 .ts 可直接重定向；含 @vben/* 的 .vue 请拆成 .ts 常量再 override。
 */
export function createOpenHisProjectOverridePlugin(webEleDir: string): Plugin | null {
  const projectDir = getOpenHisProjectDir(webEleDir);
  if (!projectDir) {
    return null;
  }

  const projectId = resolveOpenHisProjectId()!;
  const overridesDir = path.join(projectDir, 'overrides');
  const hasOverrides = fs.existsSync(overridesDir);
  const overrideCount = hasOverrides ? walkOverrideFiles(overridesDir).length : 0;

  return {
    name: 'openhis-project-override',
    enforce: 'pre',
    resolveId(source) {
      if (!hasOverrides || !source.startsWith('#/')) {
        return null;
      }

      const rel = source.slice(2);
      const overrideBase = path.join(overridesDir, rel);
      return resolveExistingFile(overrideBase);
    },
    config() {
      console.log(
        `[openhis-project] project-${projectId}${hasOverrides ? ` (overrides: ${overrideCount})` : ' (env only)'}`,
      );
    },
  };
}

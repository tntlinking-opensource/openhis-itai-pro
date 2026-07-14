#!/usr/bin/env node
/**
 * 带院点 env 启动 web-ele 构建/开发。
 *
 * 用法：
 *   OPENHIS_PROJECT=H22010402403 node scripts/openhis-project/run-web-ele.mjs -- pnpm vite build --mode production
 *   node scripts/openhis-project/run-web-ele.mjs --project H22010402403 -- pnpm vite --mode development
 */
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(__dirname, '../..');
const webEleDir = join(uiRoot, 'apps/web-ele');

function parseEnvFile(content) {
  const env = {};
  for (const line of content.split(/\r?\n/u)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    const idx = trimmed.indexOf('=');
    if (idx < 1) {
      continue;
    }
    env[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
  }
  return env;
}

function normalizeProjectId(value) {
  const raw = String(value ?? '').trim();
  if (!raw) {
    return '';
  }
  return raw.startsWith('project-') ? raw.slice('project-'.length) : raw;
}

function parseArgs(argv) {
  let project = normalizeProjectId(process.env.OPENHIS_PROJECT);
  const command = [];

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--project' && argv[i + 1]) {
      project = normalizeProjectId(argv[i + 1]);
      i += 1;
      continue;
    }
    if (arg === '--') {
      command.push(...argv.slice(i + 1));
      break;
    }
    command.push(arg);
  }

  return { project, command };
}

function detectMode(command) {
  const modeIndex = command.indexOf('--mode');
  if (modeIndex >= 0 && command[modeIndex + 1]) {
    return command[modeIndex + 1];
  }
  if (command.some((item) => item.includes('development'))) {
    return 'development';
  }
  return 'production';
}

function normalizeCommand(command) {
  if (command.length >= 2 && command[0] === 'pnpm' && command[1] === 'vite') {
    return ['pnpm', 'exec', 'vite', ...command.slice(2)];
  }

  return command;
}

function usesShell(command) {
  if (process.platform !== 'win32') {
    return false;
  }

  const executable = command[0] ?? '';
  return /\.(cmd|bat)$/i.test(executable);
}

function quoteForShell(value) {
  return /[\s"]/u.test(value) ? `"${value.replace(/"/g, '\\"')}"` : value;
}

function spawnCommand(command, options) {
  if (usesShell(command)) {
    const commandLine = command.map(quoteForShell).join(' ');
    return spawnSync(commandLine, {
      ...options,
      shell: true,
    });
  }

  return spawnSync(command[0], command.slice(1), {
    ...options,
    shell: false,
  });
}

function forwardSpawnOutput(result) {
  if (result.stdout?.length) {
    process.stdout.write(result.stdout);
  }
  if (result.stderr?.length) {
    process.stderr.write(result.stderr);
  }
}

function loadProjectEnv(projectId, mode) {
  const projectDir = join(uiRoot, 'projects', `project-${projectId}`);
  if (!existsSync(projectDir)) {
    console.warn(`[openhis-project] missing ${projectDir}, skip project env`);
    return;
  }

  const files = ['.env', `.env.${mode}`, '.env.local', `.env.${mode}.local`];
  for (const file of files) {
    const filePath = join(projectDir, file);
    if (!existsSync(filePath)) {
      continue;
    }
    const parsed = parseEnvFile(readFileSync(filePath, 'utf8'));
    Object.assign(process.env, parsed);
    console.log(`[openhis-project] loaded ${filePath}`);
  }
}

const { project, command } = parseArgs(process.argv.slice(2));
if (!command.length) {
  console.error(
    'Usage: node scripts/openhis-project/run-web-ele.mjs [--project ID] -- <command...>',
  );
  process.exit(1);
}

if (project) {
  process.env.OPENHIS_PROJECT = project;
  process.env.VITE_OPENHIS_PROJECT = project;
  loadProjectEnv(project, detectMode(command));
  console.log(`[openhis-project] OPENHIS_PROJECT=${project}`);
}

const normalizedCommand = normalizeCommand(command);
const usePipe =
  !process.stdout.isTTY ||
  !process.stderr.isTTY ||
  process.env.WHALEOPS_PACK === '1';
const result = spawnCommand(normalizedCommand, {
  cwd: webEleDir,
  stdio: usePipe ? 'pipe' : 'inherit',
  env: process.env,
  encoding: 'utf8',
  maxBuffer: 64 * 1024 * 1024,
});

if (usePipe) {
  forwardSpawnOutput(result);
}

if (result.error) {
  console.error(`[openhis-project] spawn failed: ${result.error.message}`);
  process.exit(1);
}

process.exit(result.status ?? 1);

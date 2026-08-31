<script setup lang="ts">
import type { InvFormPaperKind } from '#/utils/print/form-print-preference';

import { computed, onMounted, ref } from 'vue';

import { ElButton, ElMessage, ElSwitch, ElTable, ElTableColumn } from 'element-plus';

import PrintChromeFlagShortcut from '#/utils/print/components/print-chrome-flag-shortcut.vue';
import { isInvPrintScenario } from '#/utils/print/constants/print-scenario';
import { PRINT_SCENARIO_ITEMS } from '#/utils/print/constants/print-scenario-labels';
import {
  getFormPrintPreference,
  INV_FORM_PAPER_KIND_LABELS,
  INV_FORM_PRINT_PREF_KEY,
} from '#/utils/print/form-print-preference';
import { triggerOpenHisWinDownload } from '#/utils/print/openhis-win-download';
import {
  clearPrinterPreference,
  getPrinterPreference,
  listPrinterPreferences,
  setPrinterPromptEnabled,
} from '#/utils/print/printer-preference';
import { configurePrinterPreference } from '#/utils/print/resolve-printer';
import {
  probeHiprintClient,
  schedulePrintClientWarmup,
} from '#/utils/print/runtime/hiprint-client';
import { PRINT_PLUGIN_INSTALL_MESSAGE } from '#/utils/print/runtime/print-user-error';

defineOptions({ name: 'ProfilePrintSetting' });

interface PrintSettingRow {
  scenarioCode: string;
  label: string;
  printerName: string;
  skipPrompt: boolean;
  /** 库房单据默认纸张；其它场景为空 */
  paperKind?: InvFormPaperKind;
}

const loading = ref(false);
const clientReady = ref<boolean | null>(null);
const rows = ref<PrintSettingRow[]>([]);

/** 库房单据共用的默认纸张 */
function resolveInvPaperKind(): InvFormPaperKind | undefined {
  return getFormPrintPreference(INV_FORM_PRINT_PREF_KEY).paperKind;
}

/** 从 localStorage 刷新表格行 */
function refreshRows(): void {
  const prefs = listPrinterPreferences();
  const invPaperKind = resolveInvPaperKind();
  rows.value = PRINT_SCENARIO_ITEMS.map((item) => {
    const pref = prefs[item.scenarioCode] ?? getPrinterPreference(item.scenarioCode);
    const isInv = isInvPrintScenario(item.scenarioCode);
    return {
      scenarioCode: item.scenarioCode,
      label: item.label,
      printerName: pref?.printerName?.trim() ?? '',
      skipPrompt: pref?.skipPrompt !== false,
      paperKind: isInv ? invPaperKind : undefined,
    };
  });
}

/** 探测 Hiprint 打印客户端是否可连接（浏览器侧唯一可验证项） */
async function probeClient(): Promise<void> {
  loading.value = true;
  try {
    const probe = await probeHiprintClient();
    clientReady.value = probe.ready;
  } finally {
    loading.value = false;
  }
}

const clientStatusText = computed(() => {
  if (clientReady.value === null) {
    return '检测中…';
  }
  if (clientReady.value) {
    return '打印客户端已连接';
  }
  return '无法连接打印客户端，请按下方步骤排查';
});

const showTroubleshootingGuide = computed(() => clientReady.value === false);

/** 打开弹窗设置或修改默认打印机（库房场景同时改纸张） */
async function handleConfigure(scenarioCode: string): Promise<void> {
  const saved = await configurePrinterPreference(scenarioCode);
  if (saved) {
    const message = isInvPrintScenario(scenarioCode) ? '纸张与打印机已保存' : '默认打印机已保存';
    ElMessage.success(message);
    refreshRows();
  }
}

/** 切换是否每次打印前询问 */
function handlePromptToggle(scenarioCode: string, promptEveryTime: boolean): void {
  setPrinterPromptEnabled(scenarioCode, promptEveryTime);
  refreshRows();
}

/** 清除某场景的默认打印机 */
function handleClear(scenarioCode: string): void {
  clearPrinterPreference(scenarioCode);
  ElMessage.success('已清除默认打印机');
  refreshRows();
}

/** 下载服务器上的 OpenHIS.Win 安装包 */
async function handleDownloadOpenHisWin(): Promise<void> {
  try {
    await triggerOpenHisWinDownload();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败，请稍后重试');
  }
}

onMounted(() => {
  refreshRows();
  schedulePrintClientWarmup();
  void probeClient();
});
</script>

<template>
  <div class="max-w-4xl">
    <div class="mb-2 text-base font-medium text-[var(--el-text-color-primary)]">打印设置</div>
    <p class="mb-4 text-sm text-[var(--el-text-color-regular)]">
      默认打印机保存在本机浏览器，与当前登录用户绑定。换电脑或清除浏览器数据后需重新设置。
      库房单据默认三联单，可在此改为 A4。
    </p>
    <p
      class="mb-4 text-sm"
      :class="
        clientReady ? 'text-[var(--el-color-success)]' : 'text-[var(--el-text-color-regular)]'
      "
    >
      {{ clientStatusText }}
    </p>
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <ElButton type="primary" plain @click="handleDownloadOpenHisWin">
        下载 OpenHIS.Win 客户端
      </ElButton>
      <span class="text-xs text-[var(--el-text-color-secondary)]">
        从服务器下载安装包，安装后托盘启动即可连接打印服务
      </span>
    </div>
    <div v-if="showTroubleshootingGuide" class="mb-4">
      <p
        class="whitespace-pre-line rounded border border-[var(--el-color-warning-light-5)] bg-[var(--el-color-warning-light-9)] px-3 py-2 text-sm leading-relaxed text-[var(--el-text-color-regular)]"
      >
        {{ PRINT_PLUGIN_INSTALL_MESSAGE }}
      </p>
      <p
        class="mt-4 rounded border border-[var(--el-color-warning-light-5)] bg-[var(--el-color-warning-light-9)] px-3 py-2"
      >
        <PrintChromeFlagShortcut />
      </p>
      <p class="mt-2 text-sm text-[var(--el-text-color-regular)]">
        完成上述步骤后，请点击「重新检测」确认连接状态。
      </p>
      <ElButton class="mt-2" :loading="loading" @click="probeClient">重新检测</ElButton>
    </div>
    <ElTable v-loading="loading" :data="rows" border stripe max-height="420">
      <ElTableColumn prop="label" label="打印场景" min-width="120" />
      <ElTableColumn label="默认打印机" min-width="180">
        <template #default="{ row }">
          <span v-if="row.printerName" class="text-[var(--el-text-color-primary)]">
            {{ row.printerName }}
          </span>
          <span v-else class="text-[var(--el-text-color-regular)]">未设置</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="默认纸张" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.paperKind" class="text-[var(--el-text-color-primary)]">
            {{ INV_FORM_PAPER_KIND_LABELS[row.paperKind] }}
          </span>
          <span v-else class="text-[var(--el-text-color-regular)]">—</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="每次询问" width="120" align="center">
        <template #default="{ row }">
          <ElSwitch
            :model-value="!row.skipPrompt"
            :disabled="!row.printerName"
            @change="(value) => handlePromptToggle(row.scenarioCode, value === true)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="160" align="center">
        <template #default="{ row }">
          <ElButton link type="primary" @click="handleConfigure(row.scenarioCode)">
            {{ row.printerName ? '修改' : '设置' }}
          </ElButton>
          <ElButton
            v-if="row.printerName"
            link
            type="danger"
            @click="handleClear(row.scenarioCode)"
          >
            清除
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

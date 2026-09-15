<script setup lang="ts">
/**
 * 4103 平台结算查询结果弹窗：摘要 + setlinfo 全字段 + 各子表分区；原文折叠。
 */
import type { SettleListQuery4103Dto } from '#/api/mrms/types';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  buildSetlinfoFieldRows,
  cellText,
  firstDisplay,
  formatAdmCondTypeLabel,
  formatDiagTypeLabel,
  formatFieldCodeValue,
  formatMaindiagFlagLabel,
  formatMainOprnFlagLabel,
  formatMedChrgitmTypeLabel,
  formatSettle4103Money,
  formatValiFlagLabel,
  pickPlatformArray,
  pickPlatformRowId,
  resolveStasTypeCode,
  stasTypeTagType,
  sumIteminfoAmt,
} from './mrms-settle-query-4103.util';

defineOptions({ name: 'MrmsSettleQuery4103Dialog' });

interface Props {
  /** 是否显示 */
  modelValue?: boolean;
  /** 4103 查询结果 */
  result?: SettleListQuery4103Dto | null;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  result: null,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const rawExpanded = ref<string[]>([]);
const detailTab = ref('setlinfo');

/** 弹窗显隐双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

watch(
  () => props.modelValue,
  (open) => {
    // 每次打开：摘要页签优先，原文收起
    if (open) {
      rawExpanded.value = [];
      detailTab.value = 'setlinfo';
    }
  },
);

/** 平台 output 根 */
const platformOutput = computed(() => {
  const output = props.result?.platformOutput;
  if (output && typeof output === 'object') {
    return output as Record<string, unknown>;
  }
  return null;
});

/** setlinfo Map（接口字段或 output 内） */
const setlinfoMap = computed(() => {
  const fromDto = props.result?.setlinfo;
  if (fromDto && typeof fromDto === 'object') {
    return fromDto as Record<string, unknown>;
  }
  const output = platformOutput.value;
  if (output?.setlinfo && typeof output.setlinfo === 'object') {
    return output.setlinfo as Record<string, unknown>;
  }
  return null;
});

/** 状态码（可从 setlinfo 回落） */
const statusCode = computed(() => {
  const row = props.result;
  const fromDto = resolveStasTypeCode(row?.setlStatusCode, row?.setlStatusText);
  if (fromDto) {
    return fromDto;
  }
  const info = setlinfoMap.value;
  if (!info) {
    return '';
  }
  return resolveStasTypeCode(String(info.stas_type ?? ''), String(info.setl_list_stas ?? ''));
});

/** 总费用：优先平台 medfee_sumamt；否则从 iteminfo 正确合计（勿用可能翻倍的后端摘要） */
const displayFee = computed(() => {
  const fromInfo = setlinfoMap.value?.medfee_sumamt;
  if (fromInfo != null && fromInfo !== '') {
    const num = Number(fromInfo);
    if (Number.isFinite(num)) {
      return num;
    }
  }
  const fromItems = sumIteminfoAmt(platformOutput.value);
  if (fromItems != null) {
    return fromItems;
  }
  return props.result?.medfeeSumamt ?? null;
});

/** setlinfo 描述行 */
const setlinfoRows = computed(() => buildSetlinfoFieldRows(setlinfoMap.value));

/** 诊断 */
const diseRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'diseinfo'));
/** 收费项 */
const itemRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'iteminfo'));
/** 费用分类是否有有效标志（无则不展示空列） */
const itemHasValiFlag = computed(() =>
  itemRows.value.some((row) => {
    const flag = row.vali_flag;
    return flag != null && String(flag).trim() !== '';
  }),
);
/** 手术 */
const oprnRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'oprninfo'));
/** 基金分项 */
const payRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'payinfo'));
/** 门慢门特 */
const opspRows = computed(() =>
  pickPlatformArray(platformOutput.value ?? undefined, 'opspdiseinfo'),
);
/** 重症 */
const icuRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'icuinfo'));
/** 输血 */
const bldRows = computed(() => pickPlatformArray(platformOutput.value ?? undefined, 'bldinfo'));

/**
 * 关闭弹窗。
 */
function handleClose() {
  visible.value = false;
}

/** 原文 JSON */
const rawJson = computed(() => {
  const row = props.result;
  if (!row) return '';
  const payload = row.platformOutput || row.setlinfo || row;
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return String(payload);
  }
});
</script>

<template>
  <ElDialog
    v-model="visible"
    title="结算清单查询（4103）"
    width="96%"
    top="4vh"
    destroy-on-close
    append-to-body
    class="mrms-4103-dialog"
  >
    <template v-if="result">
      <div class="mrms-4103-summary mb-3">
        <ElDescriptions :column="3" border size="default">
          <ElDescriptionsItem label="患者">
            {{ firstDisplay(result.psnName, setlinfoMap?.psn_name) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="病案号">
            {{ firstDisplay(result.medcasno, setlinfoMap?.medcasno) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="人员编号">
            {{ firstDisplay(result.psnNo, setlinfoMap?.psn_no) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="结算 ID">
            {{ firstDisplay(result.setlId, setlinfoMap?.setl_id) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="就诊 ID">
            {{ firstDisplay(setlinfoMap?.mdtrt_id) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="清单流水号">
            {{
              firstDisplay(result.setlListId, setlinfoMap?.setl_list_sn, setlinfoMap?.setl_list_id)
            }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="结算/申报时间">
            {{ firstDisplay(result.setlTime, setlinfoMap?.dcla_time, setlinfoMap?.setl_end_date) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="总费用">
            <span class="mrms-4103-fee">{{ formatSettle4103Money(displayFee) }}</span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="平台状态">
            <ElTag :type="stasTypeTagType(statusCode)" size="default" effect="dark">
              {{ formatFieldCodeValue('stas_type', statusCode) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="平台消息">
            {{ firstDisplay(result.platformMsg, setlinfoMap?.chk_cont) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>

      <ElTabs v-model="detailTab" type="border-card" class="mrms-4103-tabs">
        <ElTabPane :label="`清单信息（${setlinfoRows.length}）`" name="setlinfo">
          <ElEmpty v-if="setlinfoRows.length === 0" description="无 setlinfo" />
          <ElDescriptions v-else :column="3" border size="small">
            <ElDescriptionsItem v-for="item in setlinfoRows" :key="item.key" :label="item.label">
              {{ item.value }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElTabPane>

        <ElTabPane :label="`诊断（${diseRows.length}）`" name="diseinfo">
          <ElEmpty v-if="diseRows.length === 0" description="无诊断子表" />
          <ElTable v-else :data="diseRows" border stripe size="small" max-height="420">
            <ElTableColumn label="主诊" width="72" align="center">
              <template #default="{ row }">
                {{ formatMaindiagFlagLabel(row.maindiag_flag) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="类别" width="100" align="center">
              <template #default="{ row }">
                {{ formatDiagTypeLabel(row.diag_type) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="诊断编码" width="120" align="center" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.diag_code) }}</template>
            </ElTableColumn>
            <ElTableColumn label="诊断名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.diag_name) }}</template>
            </ElTableColumn>
            <ElTableColumn label="入院病情" width="130" align="center">
              <template #default="{ row }">
                {{ formatAdmCondTypeLabel(row.adm_cond_type) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="诊断清单ID" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ pickPlatformRowId(row, 'setl_list_diag_id', 'etl_list_diag_id') }}
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`费用分类（${itemRows.length}）`" name="iteminfo">
          <ElEmpty v-if="itemRows.length === 0" description="无收费项目子表" />
          <ElTable v-else :data="itemRows" border stripe size="small" max-height="420">
            <ElTableColumn label="项目类别" width="140" align="center" fixed="left">
              <template #default="{ row }">
                {{ formatMedChrgitmTypeLabel(row.med_chrgitm_type) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="金额" width="110" align="right" header-align="right">
              <template #default="{ row }">{{ formatSettle4103Money(row.amt) }}</template>
            </ElTableColumn>
            <ElTableColumn label="甲类合计" width="110" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatSettle4103Money(row.claa_sumfee) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="乙类金额" width="110" align="right" header-align="right">
              <template #default="{ row }">{{ formatSettle4103Money(row.clab_amt) }}</template>
            </ElTableColumn>
            <ElTableColumn label="全自费" width="110" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatSettle4103Money(row.fulamt_ownpay_amt) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="其他" width="100" align="right" header-align="right">
              <template #default="{ row }">{{ formatSettle4103Money(row.oth_amt) }}</template>
            </ElTableColumn>
            <ElTableColumn label="项目清单ID" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ pickPlatformRowId(row, 'etl_list_chrgitm_id', 'setl_list_chrgitm_id') }}
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="itemHasValiFlag" label="有效" width="80" align="center">
              <template #default="{ row }">
                {{ formatValiFlagLabel(row.vali_flag) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`手术（${oprnRows.length}）`" name="oprninfo">
          <ElEmpty v-if="oprnRows.length === 0" description="无手术子表" />
          <ElTable v-else :data="oprnRows" border stripe size="small" max-height="420">
            <ElTableColumn label="主手术" width="100" align="center">
              <template #default="{ row }">
                {{ formatMainOprnFlagLabel(row.main_oprn_flag) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="手术编码" width="120" align="center" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.oprn_oprt_code) }}</template>
            </ElTableColumn>
            <ElTableColumn label="手术名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.oprn_oprt_name) }}</template>
            </ElTableColumn>
            <ElTableColumn label="手术日期" width="120" align="center">
              <template #default="{ row }">{{ cellText(row.oprn_oprt_date) }}</template>
            </ElTableColumn>
            <ElTableColumn label="麻醉方式" width="100" align="center">
              <template #default="{ row }">{{ cellText(row.anst_way) }}</template>
            </ElTableColumn>
            <ElTableColumn label="术者" width="100" align="center">
              <template #default="{ row }">{{ cellText(row.oper_dr_name) }}</template>
            </ElTableColumn>
            <ElTableColumn label="麻醉医师" width="100" align="center">
              <template #default="{ row }">{{ cellText(row.anst_dr_name) }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`基金分项（${payRows.length}）`" name="payinfo">
          <ElEmpty v-if="payRows.length === 0" description="无基金分项" />
          <ElTable v-else :data="payRows" border stripe size="small" max-height="420">
            <ElTableColumn label="基金支付类型" width="120" align="center" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.fund_pay_type) }}</template>
            </ElTableColumn>
            <ElTableColumn label="统筹区基金类型" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ cellText(row.poolarea_fund_pay_name || row.poolarea_fund_pay_type) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="基金支付金额" width="120" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatSettle4103Money(row.fund_payamt) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`门慢门特（${opspRows.length}）`" name="opspdiseinfo">
          <ElEmpty v-if="opspRows.length === 0" description="无门慢门特子表" />
          <ElTable v-else :data="opspRows" border stripe size="small" max-height="420">
            <ElTableColumn label="诊断编码" width="120" align="center">
              <template #default="{ row }">{{ cellText(row.diag_code) }}</template>
            </ElTableColumn>
            <ElTableColumn label="诊断名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.diag_name) }}</template>
            </ElTableColumn>
            <ElTableColumn label="手术操作" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ cellText(row.oprn_oprt_name) }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`重症（${icuRows.length}）`" name="icuinfo">
          <ElEmpty v-if="icuRows.length === 0" description="无重症监护子表" />
          <ElTable v-else :data="icuRows" border stripe size="small" max-height="420">
            <ElTableColumn label="病房类型" width="120" align="center">
              <template #default="{ row }">
                {{ cellText(row.scs_cutd_ward_type) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="进入时间" width="170" align="center">
              <template #default="{ row }">
                {{ cellText(row.scs_cutd_inpool_time) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="退出时间" width="170" align="center">
              <template #default="{ row }">
                {{ cellText(row.scs_cutd_exit_time) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="合计时长" width="120" align="center">
              <template #default="{ row }">
                {{ cellText(row.scs_cutd_sum_dura) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane :label="`输血（${bldRows.length}）`" name="bldinfo">
          <ElEmpty v-if="bldRows.length === 0" description="无输血子表" />
          <ElTable v-else :data="bldRows" border stripe size="small" max-height="420">
            <ElTableColumn label="品种" width="100" align="center">
              <template #default="{ row }">{{ cellText(row.bld_cat) }}</template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="100" align="right" header-align="right">
              <template #default="{ row }">{{ cellText(row.bld_amt) }}</template>
            </ElTableColumn>
            <ElTableColumn label="单位" width="80" align="center">
              <template #default="{ row }">{{ cellText(row.bld_unt) }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>

      <ElCollapse v-model="rawExpanded" class="mt-3">
        <ElCollapseItem title="平台原文（JSON，排查用）" name="raw">
          <pre class="mrms-4103-raw">{{ rawJson }}</pre>
        </ElCollapseItem>
      </ElCollapse>
    </template>

    <template #footer>
      <ElButton type="primary" @click="handleClose">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.mrms-4103-fee {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-primary);
}

.mrms-4103-tabs {
  max-height: calc(92vh - 280px);
  overflow: auto;
}

.mrms-4103-raw {
  margin: 0;
  max-height: 360px;
  overflow: auto;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>

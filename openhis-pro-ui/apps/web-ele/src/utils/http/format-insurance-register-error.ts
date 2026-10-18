import { isPersistenceConcurrencyMessage } from '#/utils/http/format-persistence-error';

/** 进销存交易号 3501–3513（含查询 3508–3513）；须为独立交易号，避免雪花 ID 误匹配。 */
const INV_INFNO_RE = /(?:^|[^\d])(35(?:0[1-9]|1[0-3]))(?!\d)/;

/** 从错误原文识别进销存交易号；无交易号时用字段特征兜底。 */
function detectInventoryInfno(msg: string): null | string {
  const match = msg.match(INV_INFNO_RE);
  if (match) {
    return match[1];
  }
  // 平台有时把查询行 JSON 直接放进 errMsg，无「3510 平台失败」前缀
  if (
    msg.includes('fixmedinsBchno') ||
    msg.includes('medinsProdPurcNo') ||
    msg.includes('splerName') ||
    msg.includes('medinsListNames')
  ) {
    return '3510';
  }
  return null;
}

/** 明确为 2201 挂号登记失败（避免 admdvs「2201xx」误伤）。 */
function isOutpatientRegister2201(msg: string): boolean {
  return /(?:^|[^\d])2201(?:[^\d]|$)/.test(msg) || msg.includes('挂号登记');
}

/** 是否「没有有效的门诊定点」类跨省普通门诊拒付。 */
function isNoValidOutpatientDesignatedPoint(msg: string): boolean {
  return msg.includes('没有有效的门诊定点') || msg.includes('无有效的门诊定点');
}

/** 市医保 2201 对身份证凭证走卡串加解密失败（FMI encodeCard / 解密卡串）。 */
function isFmiIdCardCertFailure(msg: string): boolean {
  const hasFmiAuth =
    msg.includes('接口认证服务') || msg.includes('调用远程FMI') || msg.includes('FMI接口失败');
  const hasCardCipher =
    msg.includes('解密卡串') || msg.includes('encodeCard') || msg.includes('err data length');
  return hasFmiAuth || hasCardCipher;
}

/** 平台报统筹区不符等，多指向省外异地未备案/备案无效。 */
function isOffsitePoolingAreaMismatch(msg: string): boolean {
  return (
    msg.includes('统筹区与该定点所属统筹区不符') ||
    msg.includes('统筹区不符') ||
    (msg.includes('统筹区') && msg.includes('不符合业务要求'))
  );
}

/** 将医保挂号/收费/进销存等相关后端错误转为友好、可持久展示的用户文案。 */
export function formatInsuranceRegisterError(
  errorCode: string | undefined,
  rawMessage: string,
): null | { message: string; title: string } {
  const msg = String(rawMessage ?? '').trim();
  if (!msg) {
    return null;
  }

  if (isPersistenceConcurrencyMessage(msg)) {
    return null;
  }

  if (
    errorCode === 'C-PROJECT-UNMAPPED' ||
    msg.includes('无国家码映射') ||
    msg.includes('尚未映射国家医保目录编码')
  ) {
    return null;
  }

  const inventoryInfno = detectInventoryInfno(msg);
  const isRegisterInsurance =
    errorCode === 'INS-011' ||
    errorCode === 'INS-C03' ||
    errorCode === 'INS-009' ||
    errorCode === 'INS-015' ||
    errorCode === 'REG-VAL-002' ||
    errorCode === 'REG-VAL-003' ||
    isOutpatientRegister2201(msg) ||
    isNoValidOutpatientDesignatedPoint(msg) ||
    inventoryInfno != null ||
    msg.includes('医保编码') ||
    msg.includes('yb_no') ||
    msg.includes('医师编码');

  if (!isRegisterInsurance) {
    return null;
  }

  // 跨省普通门诊拒付：优先给出慢特病结算指引（收费 2206 等）
  if (isNoValidOutpatientDesignatedPoint(msg)) {
    const clean = msg.split('>>>')[0]?.trim() || msg;
    return {
      title: '医保结算失败：无有效门诊定点',
      message: [
        clean,
        '',
        '处理建议：',
        '· 该患者可能是跨省就医，普通门诊（医疗类别 11）需参保地门诊定点',
        '· 若已有慢特病备案：请医生站导入慢病诊断，按「门诊慢病（140104）」或「门诊特病（140201）」结算',
        '· 若无慢特病备案且确需普通门诊：请患者先在参保地办理异地门诊定点/备案后再来结算',
        '· 紧急时可先自费，备案生效后再按医保政策处理',
        '',
        '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
      ].join('\n'),
    };
  }

  if (errorCode === 'INS-011' || msg.includes('yb_no') || msg.includes('医保编码')) {
    if (msg.includes('收费项缺少')) {
      const itemMatch = msg.match(/收费项缺少国家医保编码\(yb_no\)[：:]\s*([^，,]+)/);
      const itemName = itemMatch?.[1]?.trim();
      const itemHint = itemName ? `「${itemName}」` : '所选收费项';
      return {
        title: '医保收费：收费项缺少国家医保编码',
        message: [
          `${itemHint}尚未维护国家医保目录编码（yb_no），无法上传费用明细完成医保结算。`,
          '',
          '请按以下步骤处理：',
          '1. 打开【基础数据 / 耗材或药品目录】找到对应产品',
          '2. 完成医保目录对照，填写「医保编码 / yb_no」并保存',
          '3. 回到收费页刷新后重新确认收费',
          '',
          '说明：费用性质为医保时，所有待收明细都必须有国家码；无国码项目请改自费或先对码。',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
    if (msg.includes('医师') || msg.includes('接诊')) {
      const nameMatch = msg.match(/医师缺少医保编码\(yb_no\):\s*(.+)/);
      const doctorName = nameMatch?.[1]?.trim();
      const doctorHint = doctorName ? `（${doctorName}）` : '';
      return {
        title: '医保挂号：请先完善医师编码',
        message: [
          `当前所选接诊医生${doctorHint}尚未维护医保平台编码，无法完成医保挂号登记（2201）。`,
          '',
          '请按以下步骤处理：',
          '1. 打开【基础数据 → 人员管理】，找到该医生',
          '2. 填写「医保编码 / yb_no」并保存（需与长春市医保平台一致）',
          '3. 回到挂号页，重新选择该医生后再次预结算',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
    if (msg.includes('科室')) {
      return {
        title: '医保挂号：请先完善科室编码',
        message: [
          '当前挂号科室尚未维护医保平台编码，无法完成医保挂号登记（2201）。',
          '',
          '请按以下步骤处理：',
          '1. 打开【基础数据 → 科室/服务位置】维护该科室的「医保编码」',
          '2. 保存后回到挂号页重新预结算',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
    if (msg.includes('须选择接诊医师') || msg.includes('未指定')) {
      return {
        title: '医保挂号：请选择接诊医生',
        message: [
          '医保挂号登记需要指定一名接诊医师。',
          '',
          '请在挂号表单中选择「医生」后再点击预结算。',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
  }

  if (msg.includes('医师编码') && (msg.includes('-') || msg.includes('校验不通过'))) {
    return {
      title: '医保挂号：医师编码无效',
      message: [
        '医保平台拒绝了本次挂号登记：上传的医师编码无效（可能未维护或填写的不是平台编码）。',
        '',
        '请确认挂号医生已在主数据中维护正确的「医保编码 / yb_no」，然后重试。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'REG-VAL-003') {
    return {
      title: '医保挂号预结算条件不满足',
      message: [
        msg || '当前无法完成医保挂号预结算。',
        '',
        '常见原因：未装配医保模块、费用性质非医保类、或未选择正确的医保费用性质。',
        '请核对费用性质与读卡信息后重试。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'INS-015' || msg.includes('重新展码') || msg.includes('刷新预结算')) {
    return {
      title: '医保电子凭证已失效',
      message: [
        '医保电子凭证可能已过期或平台要求重新展码。',
        '',
        '请先点击「重新扫医保码并刷新预结算」，成功后再确认缴费。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'REG-VAL-002') {
    return {
      title: '请先读卡获取医保人员信息',
      message: [
        '医保挂号须先读社保卡/医保码，获取人员编号（psn_no）后再预结算。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (
    msg.includes('未查到基础目录信息') ||
    msg.includes('查询结算目录信息失败') ||
    msg.includes('未对码项目')
  ) {
    const clean = msg.split('>>>')[0]?.trim() || msg;
    return {
      title: '医保收费：目录未对码',
      message: [
        clean,
        '',
        '请按上面点名的药品、耗材或诊疗，在目录里维护正确的国家医保编码后再上传。',
        '耗材上传只用医保码前 15 位，平台按这段码查基础目录。',
        '',
        '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
      ].join('\n'),
    };
  }

  if (
    msg.includes('2204') ||
    msg.includes('费用上传') ||
    (msg.includes('2301') && msg.includes('费用'))
  ) {
    const clean = msg.split('>>>')[0]?.trim() || msg;
    // 长春 feeListDBO.41 / feeListDBO_41：费用明细主键冲突（同 feedetl_sn 重复 2204）
    if (
      /feeListDBO[._]?41/i.test(msg) ||
      msg.includes('请勿插入重复的主键') ||
      msg.includes('请勿输入重复的值')
    ) {
      return {
        title: '医保收费：费用明细上传失败（流水号重复）',
        message: [
          clean,
          '',
          '原因说明：平台上该费用明细流水号（feedetl_sn）已存在，重复上传被拒。',
          '',
          '处理建议：',
          '1. 关闭收费窗后重新打开该患者，再点「医保收费」（系统会先 2205 撤销未结算明细）',
          '2. 若仍失败：该流水可能已在平台正式结算（2207），需先医保退费/冲正（2208）后再收',
          '3. 核对同就诊是否有「预结算成功但收费失败」的悬挂批次',
          '',
          '若仍失败，请联系信息科，提供就诊号、批次号与上述技术详情。',
        ].join('\n'),
      };
    }
    return {
      title: '医保收费：费用明细上传失败',
      message: [
        clean,
        '',
        '常见原因：',
        '· 费用明细流水号重复（feeListDBO.41）：上次预结算明细未撤销干净',
        '· 数量/单价/金额不一致（折片后须满足 金额=单价×数量）',
        '· 耗材/药品「医保编码」未维护或位数不符合平台规则（耗材上传截前 15 位）',
        '· 当日医保签到（9001）失效，可尝试重新读卡签到',
        '',
        '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
      ].join('\n'),
    };
  }

  // INS-C03/INS-009 为通用平台失败码，须按交易号区分文案，禁止一律套「挂号登记」
  if (errorCode === 'INS-C03' || errorCode === 'INS-009' || isOutpatientRegister2201(msg)) {
    const clean = msg.split('>>>')[0]?.trim() || msg;

    if (inventoryInfno) {
      return {
        title: `医保进销存（${inventoryInfno}）调用失败`,
        message: [
          clean,
          '',
          '常见原因：',
          '· 当日医保签到（9001）失效，请先重新签到后再查',
          '· 查询条件与平台数据不一致（批次流水号、机构目录编码、起止日期等）',
          '· 医保专网或平台短暂异常',
          '',
          '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
        ].join('\n'),
      };
    }

    if (isOutpatientRegister2201(msg)) {
      if (isFmiIdCardCertFailure(msg)) {
        return {
          title: '医保挂号登记失败：就诊凭证不被平台接受',
          message: [
            '医保平台在门诊挂号登记（2201）时校验就诊凭证失败（接口认证/FMI 卡串处理异常）。',
            '市医保对身份证（02）需上送读卡第二缓冲 SignBuff 作卡密；若仍为明文身份证号，或 SignBuff 为空，易触发本错误。',
            '',
            '请按以下步骤处理：',
            '1. 确认本机 Zxplat.Win 已更新，重新读身份证（应取得 SignBuff 卡密）后再挂号',
            '2. 若读卡仍无卡密：改扫「医保电子凭证」或刷「社保卡」',
            '3. 确认当日医保签到（9001）有效后重试',
            '',
            `技术详情：${clean}`,
          ].join('\n'),
        };
      }
      return {
        title: '医保平台挂号登记失败',
        message: [
          clean,
          '',
          '常见原因：',
          '· 医师/科室医保编码未维护或填写错误',
          '· 当日医保签到（9001）失效，可尝试重新读卡签到',
          '· 医保专网或平台短暂异常',
          '· 身份证读卡后市医保建档失败：请改扫医保电子凭证或刷社保卡',
          '',
          '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
        ].join('\n'),
      };
    }

    // 统筹区不符：标题与【重要提示】前置，避免淹没在常见原因列表里
    if (isOffsitePoolingAreaMismatch(clean)) {
      return {
        title: '医保平台调用失败：请先核对异地备案',
        message: [
          clean,
          '',
          '【重要提示】患者属于异地患者，可能没有备案，请检查备案信息。',
          '',
          '常见原因：',
          '· 省外异地就医未办理定点/转诊备案，或备案未生效、已过期',
          '· 当日医保签到（9001）失效，可尝试重新签到',
          '· 业务入参与平台校验规则不符',
          '· 医保专网或平台短暂异常',
          '',
          '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
        ].join('\n'),
      };
    }

    // 通用平台失败：不提示异地备案（仅 isOffsitePoolingAreaMismatch 等异地特征才提示）
    return {
      title: '医保平台调用失败',
      message: [
        clean,
        '',
        '常见原因：',
        '· 当日医保签到（9001）失效，可尝试重新签到',
        '· 业务入参与平台校验规则不符',
        '· 医保专网或平台短暂异常',
        '',
        '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
      ].join('\n'),
    };
  }

  return {
    title: '医保业务遇到问题',
    message: [msg, '', '请核对签到状态与业务入参后重试；仍无法解决请联系信息科。'].join('\n'),
  };
}

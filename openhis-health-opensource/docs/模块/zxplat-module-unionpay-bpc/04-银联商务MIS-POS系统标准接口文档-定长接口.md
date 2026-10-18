> **文档性质**：银联商务官方接口规范（定长接口 · Windows）— 由 Word 转换，供 zxplat-module-unionpay-bpc 设计参考  
> **源文件**：`04 银联商务MIS-POS系统标准接口文档--定长接口.docx`  
> **转换工具**：Pandoc · **日期**：2026-06-13  
> **图片资源**：`./04-mis-pos-media/`（若缺失，请自 docx 源文件重新导出）

---
![](./04-mis-pos-media/media/image1.png)

**银联商务MIS-POS系统标准接口文档（windows）**

|  版本  |    日期    | 拟稿和修改 | 说明                                           |
|:------:|:----------:|:----------:|:-----------------------------------------------|
| V2.0.0 | 2016.11.30 |    李壮    | 接口中传入应用类型                             |
| V2.1.0 | 2017.01.03 |    李壮    | 传出参数增加优惠金额字段                       |
| V2.2.0 | 2017.06.30 |    李壮    | 全民惠借贷记改造，增加银行卡出参借贷记标识     |
| V2.3.0 | 2017.09.15 |    李壮    | 增加无硬件B扫C应用                             |
| V3.4.0 | 2018.03.03 |    万帅    | 增加无硬件B扫C应用查询接口，订单号反向交易接口 |
| V3.5.0 | 2019.12.26 |    万帅    | 修改无硬件pos通的通讯方式和接口                |

| 本文档中的所有内容为银联商务股份有限公司专属所有。未经银联商务股份有限公司的明确书面许可，任何组织或个人不得以任何目的、任何形式及任何手段复制或传播本文档部分或全部内容。 |
|----|

**目录**

[1 前言 [1](#前言)](#前言)

[1.1 范围 [1](#范围)](#范围)

[1.2 术语及定义 [1](#术语及定义)](#术语及定义)

[1.3 系统要求 [4](#系统要求)](#系统要求)

[1.3.1 硬件要求 [4](#硬件要求)](#硬件要求)

[1.3.2 软件要求 [5](#软件要求)](#软件要求)

[1.3.3 安全要求 [5](#安全要求)](#安全要求)

[2 MIS-POS系统的调用 [6](#mis-pos系统的调用)](#mis-pos系统的调用)

[2.1 MIS-POS系统处理流程 [6](#mis-pos系统处理流程)](#mis-pos系统处理流程)

[2.2 调用方式说明 [6](#调用方式说明)](#调用方式说明)

[2.3 无界面调用（主要方式） [8](#无界面调用主要方式)](#无界面调用主要方式)

[2.3.1 交易函数 [8](#交易函数)](#交易函数)

[2.3.2 银行卡交易 [15](#银行卡交易)](#银行卡交易)

[2.3.3 有硬件POS通交易 [22](#有硬件pos通交易)](#有硬件pos通交易)

[2.3.4 无硬件POS通交易 [28](#无硬件pos通交易)](#无硬件pos通交易)

[2.3.5 传入及传出参实例 [34](#传入及传出参实例)](#传入及传出参实例)

[2.4 有界面调用 [42](#有界面调用)](#有界面调用)

[2.4.1 交易函数 [42](#交易函数-1)](#交易函数-1)

[2.4.2 请求参数 [43](#请求参数-4)](#请求参数-4)

[2.4.3 应答参数 [44](#应答参数-4)](#应答参数-4)

[2.4.4 传入及传出实例 [45](#传入及传出实例)](#传入及传出实例)

[2.5 部分开发案例 [46](#部分开发案例)](#部分开发案例)

[2.5.1 Delphi调用银行卡交易动态库（posinf.dll） [46](#delphi调用银行卡交易动态库posinf.dll)](#delphi调用银行卡交易动态库posinf.dll)

[2.5.2 IE调用银行卡交易控件（OCX）（posinf.dll） [49](#ie调用银行卡交易控件ocxposinf.dll)](#ie调用银行卡交易控件ocxposinf.dll)

[2.5.3 C#调用银行卡交易控件（posinf.dll） [51](#c调用银行卡交易控件posinf.dll)](#c调用银行卡交易控件posinf.dll)

[2.5.4 VC调用银行卡交易控件（posinf.dll） [52](#vc调用银行卡交易控件posinf.dll)](#vc调用银行卡交易控件posinf.dll)

[2.5.5 Java调用银行卡交易控件（posinf.dll） [57](#java调用银行卡交易控件posinf.dll)](#java调用银行卡交易控件posinf.dll)

[2.5.6 Delphi调用银行卡交易动态库实例（bankpos.dll） [59](#delphi调用银行卡交易动态库实例bankpos.dll)](#delphi调用银行卡交易动态库实例bankpos.dll)

[3 MIS-POS系统的操作及使用 [61](#mis-pos系统的操作及使用)](#mis-pos系统的操作及使用)

[3.1 参数配置 [61](#参数配置)](#参数配置)

[3.1.1 银行卡、POS通的参数配置 [62](#银行卡pos通的参数配置)](#银行卡pos通的参数配置)

[3.1.2 无硬件POS通的参数配置 [64](#无硬件pos通的参数配置)](#无硬件pos通的参数配置)

[3.1.3 新增应用的参数配置 [66](#新增应用的参数配置)](#新增应用的参数配置)

[3.2 测试工具（Demo）使用 [67](#测试工具demo使用)](#测试工具demo使用)

[3.2.1 无界面dll调用 [67](#无界面dll调用)](#无界面dll调用)

[3.2.2 有界面dll调用 [75](#有界面dll调用)](#有界面dll调用)

[3.2.3 无界面ocx调用 [77](#无界面ocx调用)](#无界面ocx调用)

[3.3 常见简单错误及解决思路 [79](#常见简单错误及解决思路)](#常见简单错误及解决思路)

# 前言

## 范围

银联商务MIS-POS系统是部署和运行在商户收银机中的支付插件，因常与收银机上外接的POS机具结合起来用而被称为MIS-POS系统。银商MIS-POS系统利用商户收银机的网络或POS机终端网络，采用C/S或B/S调用方式，将收银终端的交易数据传送至银联商务或银联收单平台完成支付交易。

本文档为银联商务MIS-POS系统标准版Windows接口文档，其中规定了银联商务MIS-POS系统的软硬件要求、安全要求、管理要求以及与收银机之间交互的接口和界面等方面的说明。主要提供给开发人员开发收银系统使用。

## 术语及定义

银行卡

商业银行等金融机构以及邮政储汇机构向社会发行的，具有消费信用、转账结算、存取现金等全部或部分功能的信用支付工具。

银联商务MIS-POS系统

银联商务自主开发的用于和收银终端进行交互，最终达到实现银行卡传统业务交易或增值业务交易的银行卡受理系统。

MIS商户

本文档所指MIS商户是指能够受理国内磁条卡、IC卡交易和扫码支付类交易，及有条件受理国外磁条卡或IC卡交易，所属的收银终端通过商户端前置机与银联POS前置处理系统相连的零售商、公司或其他机构。

收银终端

在MIS商户内部网络内，接受磁条卡、IC卡和扫码支付相关信息，具有通讯功能，接受柜员的指令完成金融交易信息和有关信息交换的设备，如商用第三代收银机或普通PC。

POS通

用户可以出示云闪付、微信、支付宝等手机APP中的付款码提供给商户进行扫码，通过Pos终端或Mis商户端程序完成金融交易。

消费

普通消费是指持卡人在特约商户消费时用银行卡结算的交易，经批准的消费额即时地反映到该持卡人的账户余额上。

撤销

是指因人为原因而撤销已完成的消费。消费撤销必须是撤销POS当日当批的消费交易。操作员需要向持卡人核对原始交易的凭证。发卡行批准的消费撤销金额将即时地反映到该持卡人的账户上。

消费撤销金额必须等于原始消费的金额。

消费撤销交易需要主管操作员输入密码。

消费撤销交易必须在原交易POS上进行。

退货

退货交易用来撤销POS终端当日当批以及非当日当批的消费交易。操作员需要向持卡人核对原始交易的凭证，并按业务要求在POS终端上输入原始交易的有关数据，然后向POS中心发送退货交易。

退货交易需要主管操作员输入密码。

退货交易应根据POS终端参数设置判断是否超过联机退货允许的最大退货金额。

退货交易支持部分退货和多次退货。

退货时，POS中心根据收到的交易匹配原交易，如匹配到原交易，返回成功应答，退货成功；否则返回失败应答，退货不成功，此时可根据需要转手工退货。

查余

持卡人在收银终端查询所持卡账户余额的交易。查询的结果为账户的可用余额。该交易不参加资金清算。

非接触IC卡余额查询交易，如果查询电子钱包余额，为靠近感应区即可，如果查询帐户余额，则需要插卡输入密码查询。

重打印

重打交易功能：交易成功但由于某种原因而没有完成打印签购单的情况下，用此功能补打最后一笔交易或者指定流水的签购单。重打印的签购单上必须有补打标志字样。重打印功能只适用于当批次结算前的交易。

签到

签到交易：MIS-POS系统签到采用联机交易方式，由操作员发起签到请求，经过商户端前置机向银联POS前置处理系统上送POS签到交易。银联POS前置处理系统验证商户、终端的有效性后，产生收银终端工作密钥、交易批次号、银联POS前置处理系统的交易日期和时间等数据，并下传到MIS-POS系统。收银终端只有签到成功后才可以进行金融交易。

结算

结算功能：收银终端批结算采用如下方式进行：

> a\) 操作员查阅总账信息；\
> b) 操作员确认进行批结算；\
> c) 商户端前置机直接应答收银终端结算完成，收银终端打印结算单据。商户端前置机根据记录的该收银终端当批次交易流水，与银联POS前置处理系统进行批结算，在这个过程当中，将暂时禁止该收银终端发生新的交易；\
> d) 商户端前置机与银联POS前置处理系统完成批结算过程后，收银终端自动签退，该收银终端的当批次交易转入历史交易库。发生新的交易须重新做签到交易。

这个结算过程实际包括了收银终端向商户端前置机发送结算通知和商户端前置机与银联POS前置处理系统进行批结算两个步骤。

收银终端批结算时，商户端前置机将该收银终端当批次交易的借记总金额、借记总笔数、贷记总金额、贷记总笔数等数据上送银联POS前置处理系统，并对银联POS前置处理系统的结账响应报文做处理。

其他交易

当开启预授权类交易和“全民付”交易后，可以做这两类交易，

预授权指MIS商户就持卡人预计支付金额向发卡行索取日后付款的承诺。发卡行将持卡人账户的预授权金额冻结，并给出授权号。

持卡人结账时，MIS商户将根据持卡人实际消费金额向发卡行请求资金结算。发卡行对持卡人账户按实际消费金额进行扣账，同时将预授权金额进行解冻。

如果预授权有效期内商户没有上送相应的预授权完成交易，发卡行将会自动解冻预授权的金额。

预授权金额不参与清算。

相对应预授权的，还有预授权撤销，预授权完成，预授权完成撤销交易。

“全民付”是银联商务为“服务社会，方便大众”，利用国内领先的品牌影响力，整合现有银行卡受理商户资源和终端资源，而开展的全国性便民支付服务。 依托 “全民付”终端，为广大持卡人提供水电煤缴费、信用卡还款、手机话费充值缴纳、帐单号支付等丰富业务内容，实现“全民生活便利付”的美好生活体验。

有关“全民付”交易的详细情况请咨询银联商务当地分支机构。

签购单打印

若由MIS系统打印签购单，当需要打印签购单的交易(例如消费、撤消、退货、结算、重打印、重打印结算等)时，默认会在程序安装目录所在的gmc文件夹下，生成签购单文件（默认receipt.txt），MIS系统只要打印此签购单文本即可。

## 系统要求

### 硬件要求

#### 显示屏

应可显示ASCII可视字符。

汉字显示应符合国家标准GB/T 2312汉字。

#### 键盘

必须有10个数字键，若干功能键，必须能够输入字母。键盘使用寿命应达到每键可敲击300,000次以上。

#### 通讯

通讯端口应支持以下全部或部分类型的通讯方式：

> 串口通讯；\
> 并口通讯；\
> MODEM通讯；\
> 无线通讯；\
> 网络通讯；\
> 密码键盘通讯(串口)。

#### 打印机

打印机可选用点阵击打式或热敏纸记录式打印机。能够打印可显示的ASCⅡ字符或汉字。无故障打印张数不少于50,000张收据。

### 软件要求

#### 操作系统

本接口文档，仅针对windows系列操作系统，包括windows XP、WIN7 、WIN8、 WIN10等。

#### 应用软件

收银终端软件，需要可以采用本接口文档调用模式，调用MIS-POS系统，完成银行卡刷卡步骤，同时需要记录返回数据，以备查询或查账使用。

### 安全要求

#### 收银终端要求

返回给收银终端软件的信息，均经过处理，不包含敏感信息，返回卡号也屏蔽了部分数字，收银终端软件不得截取MIS-POS系统串口和网络传输数据。

#### 网络要求

MIS-POS系统所组数据包，将通过以下几种方式进行传输。

**公网：**数据包通过公网发送到中国银联信息中心。

**前置转发：**数据包通过商户内网发至前置服务器，再由前置服务器转发至公网，最终送达中国银联信息中心。

**专线：**数据包通过专线直连，直接发往当地银联商务分公司。

其中，数据包通过公网进行传输时，已进行全报文加密。数据包通过商户内网或专线进行传输途中不得截取报文。

# MIS-POS系统的调用

## MIS-POS系统处理流程

<figure>
<img src="./04-mis-pos-media/media/image2.png" />
<figcaption><p>图 1MIS-POS系统交易处理流程</p></figcaption>
</figure>

收银终端在顾客消费时，调用MIS-POS系统，通过参数传递获取银行卡交易信息。

收银终端软件按文档叙述的调用流程提供必要交易参数，如交易类型及刷卡金额等。

MIS-POS系统受理跟银行信息中心发生的一切事务，包括签到、签退、刷信用卡及打印信用卡交易单据等，负责跟银行信息中心的信息交换。

## 调用方式说明

MIS-POS系统调用方式主要有六种：无界面DLL调用，无界面OCX调用，无界面Websocket调用，有界面EXE独立调用，有界面EXE文件传递，有界面DLL调用。

无界面DLL调用（主要方式）

无界面Dll调用方式所有函数均封装在posinf.dll里，调用函数bankall完成交易，使用前请先加载此动态库。

无界面OCX调用

Ocx调用方式需要加载插件umscox.ocx，调用函数trans()完成交易

Ocx调用方式包含如下文件（需要将gmc放在C盘根目录下，并把ocx相关文件从bankup目录拷贝到gmc目录下）：

CallOcx.htm 网页测试Demo

umsocx.ocx Ocx插件，需要放在gmc文件夹内

RegOcx.bat 注册银商ocx控件批处理命令，以管理员身份运行即可注册该控件

URegOcx.bat 注销银商Ocx控件批处理命令，以管理员身份运行可注销该控件

以管理员身份运行RegOcx.bat即可完成控件注册，以IE浏览器打开CallOcx.htm即可进行测试。快速开发可参考CallOcx.htm网页源码。

无界面Websocket调用

> Websocket调用方式需要先运行Websocket.exe，然后新建一个本地的Websocket连接，然后向此链接发送请求数据并接收响应数据。
>
> Websocket调用方式包含如下文件（需要将gmc放在C盘根目录下，并把Websocket相关文件从bankup目录拷贝到gmc目录下）：
>
> wsdemo2.htm 网页测试Demo
>
> websocket.exe Websocket可执行程序，需要放在C盘gmc文件夹内
>
> websocket.ini Websocket可执行程序依赖的配置文件，需要放在C盘gmc文件夹内
>
> RunWebsocket.bat 运行Websocket.exe的批处理命令，以管理员身份运行即可
>
> CloseWebsocekt.bat 停止运行Websocket.exe的批处理命令，以管理员身份运行即可
>
> RegWebsocket.bat Websocket设置开机自启动的批处理文件，以管理员身份运行即可注册该控件
>
> URegWebsocket.bat Websocket关闭开机自启动的批处理文件，以管理员身份运行即可注册该控件
>
> websockt自启动文件夹 Websocket另一种开机自启动的方式，把该文件夹内的文件拷贝到gmc目录下，以管理员身份执行“创建自启动.bat”批处理文件即可。

以管理员身份运行RunWebsocket.bat即可运行Websocket可执行程序，以谷歌浏览器打开wsdemo2.htm即可进行测试。快速开发可参考wsdemo2.htm网页源码。

有界面EXE独立调用

有界面EXE独立调用方式需调用方调用gmc.exe启用我方提供的操作界面供操作员使用，所有交易均通过该操作界面完成。

有界面EXE文件传递

有界面EXE文件传递与前一种方法类似，但需调用方在调用gmc.exe前，主动地在同级目录下生成一个request.txt文件作为gmc.exe交易的传入参数，随后调起gmc.exe，所有交易均通过传入参数控制，无需用户对gmc.exe界面进行操作。交易完成后，gmc.exe将应答参数生成到同目录下的response.txt中。

有界面DLL调用

有界面DLL调用方式中，被调用的函数mispos被封装在bankpos.dll中，调用方调用该函数时，DLL通过txt文件与gmc.exe进行传入和传出参数的交互。这一过程中，尽管用户可看到gmc.exe的操作界面，但交易流程由传入参数进行控制，无需用户对gmc.exe界面进行操作。

## 无界面调用（主要方式）

### 交易函数

<table style="width:98%;">
<caption><p>表 1 无界面<strong>dll调用模式</strong>交易函数说明表</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 80%" />
</colgroup>
<thead>
<tr>
<th>dll名称</th>
<th><blockquote>
<p>posinf.dll</p>
</blockquote></th>
</tr>
</thead>
<tbody>
<tr>
<td>函数名</td>
<td><blockquote>
<p>int bankall (char * request,char *response)</p>
</blockquote></td>
</tr>
<tr>
<td>功能描述</td>
<td><blockquote>
<p>交易执行函数</p>
</blockquote></td>
</tr>
<tr>
<td>入参</td>
<td><blockquote>
<p>传入定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>出参</td>
<td><blockquote>
<p>传出定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>说明</td>
<td><blockquote>
<p>所有函数均封装在posinf.dll里，调用函数bankall完成交易，使用前请先加载此动态库。</p>
<p>当该函数返回0时表示交易成功，其他表示交易失败。</p>
</blockquote></td>
</tr>
</tbody>
</table>

<table style="width:98%;">
<caption><p>表 2 无界面<strong>ocx调用模式</strong>交易函数说明表</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 80%" />
</colgroup>
<thead>
<tr>
<th>ocx名称</th>
<th><blockquote>
<p>umsocx.ocx</p>
</blockquote></th>
</tr>
</thead>
<tbody>
<tr>
<td>函数名</td>
<td><blockquote>
<p>int trans (char * bankrequest,char *BankResponse)</p>
</blockquote></td>
</tr>
<tr>
<td>功能描述</td>
<td><blockquote>
<p>交易执行函数</p>
</blockquote></td>
</tr>
<tr>
<td>请求参数</td>
<td><blockquote>
<p>传入定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>应答参数</td>
<td><blockquote>
<p>传出定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>说明</td>
<td><blockquote>
<p>Ocx调用方式需要加载插件umscox.ocx，调用函数trans()完成交易。</p>
<p>其中，classID=E083E848-B477-4F16-BCD5-72D74BC1E2AF</p>
<p>Ocx调用方式包含如下文件（需要将gmc放在C盘根目录下，并把ocx相关文件从bankup目录拷贝到gmc目录下）：</p>
<p>CallOcx.htm 网页测试Demo</p>
<p>umsocx.ocx Ocx插件，需要放在C盘gmc文件夹内</p>
<p>RegOcx.bat 注册银商ocx控件批处理命令，以管理员身份运行即可注册该控件</p>
<p>URegOcx.bat 注销银商Ocx控件批处理命令，以管理员身份运行可注销该控件</p>
<p>以管理员身份运行RegOcx.bat即可完成控件注册，以IE浏览器打开CallOcx.htm即可进行测试。快速开发可参考CallOcx.htm网页源码。</p>
</blockquote></td>
</tr>
</tbody>
</table>

<table style="width:98%;">
<caption><p>表 3 无界面<strong>Websocket调用模式</strong>交易函数说明表</p></caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 77%" />
</colgroup>
<thead>
<tr>
<th>websocket名称</th>
<th><blockquote>
<p>Websocket.exe</p>
</blockquote></th>
</tr>
</thead>
<tbody>
<tr>
<td>函数名</td>
<td><blockquote>
<p>无</p>
</blockquote></td>
</tr>
<tr>
<td>功能描述</td>
<td><blockquote>
<p>新建一个Websocket连接，然后向此发送数据和接收数据。</p>
</blockquote></td>
</tr>
<tr>
<td>请求参数</td>
<td><blockquote>
<p>传入定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>应答参数</td>
<td><blockquote>
<p>传出定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>说明</td>
<td><blockquote>
<p>Websocket调用方式需要先运行Websocket.exe，然后新建一个本地的Websocket连接，然后向此链接发送请求数据并接收响应数据。</p>
<p>Var ws = new WebSocket("ws://127.0.0.1:1818");//连接服务器</p>
<p>ws.onopen = function(event){ws.send(DUmsocx1);};//连接建立成功，向Websocket发送数据</p>
<p>ws.onmessage = function(event){alert("接收到服务器发送的数据：\r\n"+event.data);};//接收Websocket返回的数据</p>
<p>Websocket调用方式包含如下文件（需要将gmc放在C盘根目录下，并把Websocket相关文件从bankup目录拷贝到gmc目录下）：</p>
<p>wsdemo2.htm 网页测试Demo</p>
<p>websocket.exe Websocket可执行程序，需要放在C盘gmc文件夹内</p>
<p>websocket.ini Websocket可执行程序依赖的配置文件，需要放在C盘gmc文件夹内</p>
<p>RunWebsocket.bat 运行Websocket.exe的批处理命令，以管理员身份运行即可</p>
<p>CloseWebsocekt.bat 停止运行Websocket.exe的批处理命令，以管理员身份运行即可</p>
<p>RegWebsocket.bat Websocket设置开机自启动的批处理文件，以管理员身份运行即可注册该控件</p>
<p>URegWebsocket.bat Websocket关闭开机自启动的批处理文件，以管理员身份运行即可注册该控件</p>
<p>websockt自启动文件夹 Websocket另一种开机自启动的方式，把该文件夹内的文件拷贝到gmc目录下，以管理员身份执行“创建自启动.bat”批处理文件即可。</p>
<p>以管理员身份运行RunWebsocket.bat即可运行Websocket可执行程序，以谷歌浏览器打开wsdemo2.htm即可进行测试。快速开发可参考wsdemo2.htm网页源码。</p>
</blockquote></td>
</tr>
</tbody>
</table>

#### 请求参数

<table style="width:99%;">
<caption><p>表 4 无界面调用请求参数（通表）</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 16%" />
<col style="width: 14%" />
<col style="width: 50%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>N</td>
<td>2</td>
<td><p>00-银行卡</p>
<p>01-有硬件POS通</p>
<p>02-无硬件POS通</p></td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>ANS</td>
<td>8</td>
<td>不足8位时右补空格</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>ANS</td>
<td>8</td>
<td>不足8位时右补空格</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>N</td>
<td>2</td>
<td><p><strong>银行卡</strong></p>
<p>00－消费 01－撤消</p>
<p>02－退货 03－查余</p>
<p>04－重打印 05－签到</p>
<p>06－结算 07－重打结算单</p>
<p>21－预授权 23－预授权完成</p>
<p>25－预授权撤销 26－预授权完成撤销</p>
<p>30－营销联盟银行卡积分消费</p>
<p>31－银联钱包优惠券消费</p>
<p>32－银联钱包积分消费</p>
<p>33－银联钱包积分撤销</p>
<p>34－银联钱包优惠券撤销</p>
<p>35－银联钱包优惠券退货</p>
<p>36－银联钱包积分退货</p>
<p><strong>有硬件POS通B扫C</strong></p>
<p>00－消费 01－撤消</p>
<p>02－退货 03－结果查询</p>
<p>04－重打印 05－签到</p>
<p>06－结算 07－重打结算单</p>
<p>40－担保消费 41－担保撤销</p>
<p>42－担保完成 43－担保快速撤销</p>
<p>44－担保快速完成</p>
<p><strong>无硬件POS通</strong></p>
<p>00－B2C消费 01－B2C撤消</p>
<p>02－B2C退货 04－B2C结果查询</p>
<p>05－B2C签到 06－B2C结算</p>
<p>31－B2C担保 32－B2C快速担保撤销</p>
<p>33－B2C快速担保完成</p>
<p>10－C2B获取二维码 11－C2B交易查询</p>
<p>12－C2B关闭订单 13－C2B退货</p>
<p>14－C2B申请担保二维码</p>
<p>15－C2B查询担保交易状态</p>
<p>16－C2B担保撤销 17－C2B担保完成</p>
<p>注：</p>
<p>ERP系统开发查询接口尽可能避免长款，且结果查询交易必须同时判断[返回码]和[查询结果状态]；</p></td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>N</td>
<td>12</td>
<td>无小数点"."，单位为分，不足时12位左补0</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>N</td>
<td>8</td>
<td>部分应用退货及预授权类交易使用，格式为YYYYMMDD</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>N</td>
<td>12</td>
<td>部分应用退货交易使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>N</td>
<td>6</td>
<td>部分应用撤销交易使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>ANS</td>
<td>3</td>
<td>3位随机数字</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>N</td>
<td>50</td>
<td>消费时支付串码，右补空格</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>N</td>
<td>50</td>
<td><p>银商后台生成。</p>
<p>部分应用撤销，退货和查询时使用，右补空格</p></td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>N</td>
<td>50</td>
<td><p>ERP系统生成唯一标识，可用于对账。</p>
<p>部分应用查询时银商订单号和ERP订单号必须传入一个，两者皆传入则以ERP订单号为准。比如YYYYMMDDhhmmss+随机数</p></td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>N</td>
<td>32</td>
<td>二维码ID(C2B关闭二维码)</td>
</tr>
<tr>
<td style="text-align: center;">无硬件APPIDKEY</td>
<td>ANS</td>
<td>300</td>
<td>ERP传入APPIDKEY加密后的密文</td>
</tr>
</tbody>
</table>

注：该表为通表，详见对应小节（2.3.2.1，2.3.31，2.3.4.1）。

#### 应答参数

<table style="width:99%;">
<caption><p>表 5 无界面调用应答参数（通表）</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 14%" />
<col style="width: 14%" />
<col style="width: 51%" />
</colgroup>
<thead>
<tr>
<th>内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td>返回码</td>
<td>N</td>
<td>2</td>
<td>00 表示成功，其它表示失败</td>
</tr>
<tr>
<td>银行行号</td>
<td>N</td>
<td>4</td>
<td><p>银行卡应用返回银行行号</p>
<p>有硬件pos通支付宝返回[0ZFB]，微信返回[WXZF]，其他返回[POST]</p>
<p>无硬件pos通支付宝返回[0ZFB]，微信返回[WXZF]，云闪付返回[YLSM]</p>
<p>其他返回空格</p></td>
</tr>
<tr>
<td>卡号</td>
<td>ANS</td>
<td>20</td>
<td>银行卡应用返回前六后四屏蔽后卡号，其他应用空格填充</td>
</tr>
<tr>
<td>凭证号</td>
<td>N</td>
<td>6</td>
<td>原交易凭证号，部分应用撤销和查询交易使用</td>
</tr>
<tr>
<td>实付金额</td>
<td>N</td>
<td>12</td>
<td>持卡人实付金额，无硬件pos通B扫C撤销交易返回原交易金额</td>
</tr>
<tr>
<td>错误说明</td>
<td>ANS</td>
<td>40</td>
<td>中文解释</td>
</tr>
<tr>
<td>商户号</td>
<td>ANS</td>
<td>15</td>
<td>商户号</td>
</tr>
<tr>
<td>终端号</td>
<td>ANS</td>
<td>8</td>
<td>终端号</td>
</tr>
<tr>
<td>批次号</td>
<td>N</td>
<td>6</td>
<td>查询交易返回原交易批次号</td>
</tr>
<tr>
<td>交易日期</td>
<td>N</td>
<td>4</td>
<td>查询交易返回原交易日期，格式为MMDD</td>
</tr>
<tr>
<td>交易时间</td>
<td>N</td>
<td>6</td>
<td>查询交易返回原交易时间，格式为hhmmss</td>
</tr>
<tr>
<td>交易参考号</td>
<td>ANS</td>
<td>12</td>
<td>查询交易返回原交易参考号</td>
</tr>
<tr>
<td>授权号</td>
<td>N</td>
<td>6</td>
<td>预授权与担保类交易返回，其他交易空格填充</td>
</tr>
<tr>
<td>清算日期</td>
<td>N</td>
<td>4</td>
<td>空格填充，格式为MMDD</td>
</tr>
<tr>
<td>LRC校验</td>
<td>ANS</td>
<td>3</td>
<td>空格填充</td>
</tr>
<tr>
<td>优惠金额</td>
<td>N</td>
<td>12</td>
<td>优惠金额=原金额-实付金额</td>
</tr>
<tr>
<td>卡类型</td>
<td>N</td>
<td>2</td>
<td>00贷记 01借记 02准贷记</td>
</tr>
<tr>
<td><p>第三方优惠</p>
<p>说明</p></td>
<td>ANS</td>
<td>200</td>
<td>第三方优惠说明</td>
</tr>
<tr>
<td>第三方平台</td>
<td>ANS</td>
<td>50</td>
<td>第三方名称</td>
</tr>
<tr>
<td>银商订单号</td>
<td>ANS</td>
<td>50</td>
<td>消费返回</td>
</tr>
<tr>
<td>ERP订单号</td>
<td>ANS</td>
<td>50</td>
<td>空格填充</td>
</tr>
<tr>
<td>支付方式</td>
<td>N</td>
<td>1</td>
<td><p>支付方式 0：支付宝 1：微信 2：银联钱包</p>
<p>3：其他</p></td>
</tr>
<tr>
<td>查询结果状态</td>
<td>N</td>
<td>1</td>
<td><p>Pos通查询交易返回</p>
<p>0：成功</p>
<p>1：超时</p>
<p>2：已撤销</p>
<p>3：已退货</p>
<p>4：已冲正</p>
<p>5：失败</p>
<p>其他：未知错误</p></td>
</tr>
<tr>
<td>查询结果描述</td>
<td>ANS</td>
<td>50</td>
<td>查询交易返回原交易状态描述</td>
</tr>
<tr>
<td>无硬件C扫B二维码</td>
<td>ANS</td>
<td>200</td>
<td>账单二维码</td>
</tr>
<tr>
<td>无硬件C扫B账单时间</td>
<td>ANS</td>
<td>8</td>
<td>账单时间，格式为YYYYMMDD</td>
</tr>
<tr>
<td>无硬件C扫B订单状态</td>
<td>ANS</td>
<td>20</td>
<td>账单状态</td>
</tr>
</tbody>
</table>

注：该表为通表，详见对应小节（2.3.2.3，2.3.3.3，2.3.4.3）。

### 银行卡交易

#### 请求参数

<table style="width:99%;">
<caption><p>表 6 无界面调用银行卡交易请求参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 16%" />
<col style="width: 14%" />
<col style="width: 50%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>N</td>
<td>2</td>
<td>00</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>N</td>
<td>2</td>
<td><p>00－消费</p>
<p>01－撤消</p>
<p>02－退货</p>
<p>03－查余</p>
<p>04－重打印</p>
<p>05－签到</p>
<p>06－结算</p>
<p>07－重打结算单</p>
<p>21－预授权</p>
<p>23－预授权完成</p>
<p>25－预授权撤销</p>
<p>26－预授权完成撤销</p>
<p>30－营销联盟银行卡积分消费</p>
<p>31－银联钱包优惠券消费</p>
<p>32－银联钱包积分消费</p>
<p>33－银联钱包积分撤销</p>
<p>34－银联钱包优惠券撤销</p>
<p>35－银联钱包优惠券退货</p>
<p>36－银联钱包积分退货</p></td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>N</td>
<td>12</td>
<td><p>单位为分，不足时左补0</p>
<p>例如：000000000123=1.23元</p></td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>N</td>
<td>8</td>
<td>【退货】时使用，格式为YYYYMMDD，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>N</td>
<td>12</td>
<td>【退货】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>N</td>
<td>6</td>
<td>【撤销】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>ANS</td>
<td>3</td>
<td>3位随机字符</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>N</td>
<td>50</td>
<td>空格填充</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>N</td>
<td>50</td>
<td>空格填充</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>N</td>
<td>50</td>
<td>ERP系统生成的唯一交易标识，可用于查询交易和对账使用，建议格式：YYYYMMDDhhmmss+随机数</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>N</td>
<td>32</td>
<td>空格填充</td>
</tr>
<tr>
<td style="text-align: center;">无硬件APPIDKEY</td>
<td>ANS</td>
<td>300</td>
<td>空格填充</td>
</tr>
</tbody>
</table>

#### 必填要素

<table style="width:99%;">
<caption><p>表 7 无界面调用银行卡普通交易请求参数必填要素</p></caption>
<colgroup>
<col style="width: 13%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">字段</th>
<th>消费</th>
<th>撤销</th>
<th>退货</th>
<th>查余额</th>
<th>重打印</th>
<th>签到</th>
<th>结算</th>
<th><p>重打</p>
<p>结算单</p></th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>●</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>□</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
</tbody>
</table>

<table style="width:99%;">
<caption><p>表 8 无界面调用银行卡预授权类交易请求参数必填要素</p></caption>
<colgroup>
<col style="width: 13%" />
<col style="width: 21%" />
<col style="width: 21%" />
<col style="width: 21%" />
<col style="width: 21%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">字段</th>
<th>预授权</th>
<th>预授权完成</th>
<th>预授权撤销</th>
<th><p>预授权完成</p>
<p>撤销</p></th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>○</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>○</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
</tbody>
</table>

| 字段 | 营销联盟银行卡积分消费 | 银联钱包优惠券消费 | 银联钱包积分消费 | 银联钱包积分撤销 | 银联钱包优惠券撤销 | 银联钱包优惠券退货 | 银联钱包积分退货 |
|:--:|----|----|----|----|----|----|----|
| 应用类型 | ● | ● | ● | ● | ● | ● | ● |
| POS机号 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| POS员工号 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| 交易类型标志 | ● | ● | ● | ● | ● | ● | ● |
| 金额 | ● | ● | ● | ● | ● | ● | ● |
| 原交易日期 | ○ | ○ | ○ | ○ | ○ | ● | ● |
| 原交易参考号 | ○ | ○ | ○ | ○ | ○ | ● | ● |
| 原凭证号 | ○ | ○ | ○ | ● | ● | ○ | ○ |
| LRC校验 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| 串码 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| 银商订单号 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| ERP订单号 | ○ | ○ | ○ | ○ | ○ | ○ | ○ |
| 无硬件C扫B二维码ID | ○ | ○ | ○ | ○ | ○ | ○ | ○ |

表 9 无界面调用银行卡营销联盟类交易请求参数必填要素

●：必须填入实际值的字段。

○：可随意填的字段，采用空格填充，此字段在该交易中无意义，可能会被打印或被校验。

□：可选字段，该字段在交易中有意义，但填入与否不影响交易成功。

#### 应答参数

<table style="width:99%;">
<caption><p>表 10 无界面调用银行卡交易应答参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 14%" />
<col style="width: 14%" />
<col style="width: 51%" />
<col style="width: 0%" />
</colgroup>
<thead>
<tr>
<th>内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
<th></th>
</tr>
</thead>
<tbody>
<tr>
<td>返回码</td>
<td>N</td>
<td>2</td>
<td colspan="2">00表示成功，其他失败</td>
</tr>
<tr>
<td>银行行号</td>
<td>N</td>
<td>4</td>
<td colspan="2">银行卡行号</td>
</tr>
<tr>
<td>卡号</td>
<td>ANS</td>
<td>20</td>
<td colspan="2">返回前六后四屏蔽后的卡号</td>
</tr>
<tr>
<td>凭证号</td>
<td>N</td>
<td>6</td>
<td colspan="2">【撤销】交易使用</td>
</tr>
<tr>
<td>实付金额</td>
<td>N</td>
<td>12</td>
<td colspan="2">实付金额</td>
</tr>
<tr>
<td>错误说明</td>
<td>ANS</td>
<td>40</td>
<td colspan="2">错误说明中文解释</td>
</tr>
<tr>
<td>商户号</td>
<td>ANS</td>
<td>15</td>
<td colspan="2">商户号</td>
</tr>
<tr>
<td>终端号</td>
<td>ANS</td>
<td>8</td>
<td colspan="2">终端号</td>
</tr>
<tr>
<td>批次号</td>
<td>N</td>
<td>6</td>
<td colspan="2">批次号</td>
</tr>
<tr>
<td>交易日期</td>
<td>N</td>
<td>4</td>
<td colspan="2">交易日期，格式为MMDD</td>
</tr>
<tr>
<td>交易时间</td>
<td>N</td>
<td>6</td>
<td colspan="2">交易时间，格式为hhmmss</td>
</tr>
<tr>
<td>交易参考号</td>
<td>ANS</td>
<td>12</td>
<td colspan="2">【退货】交易使用</td>
</tr>
<tr>
<td>授权号</td>
<td>N</td>
<td>6</td>
<td colspan="2">【预授权】类交易使用</td>
</tr>
<tr>
<td>清算日期</td>
<td>N</td>
<td>4</td>
<td colspan="2">清算日期，格式为MMDD</td>
</tr>
<tr>
<td>LRC校验</td>
<td>ANS</td>
<td>3</td>
<td colspan="2">传入参数中的3位随机字符</td>
</tr>
<tr>
<td>优惠金额</td>
<td>N</td>
<td>12</td>
<td colspan="2">优惠金额=原金额-实付金额</td>
</tr>
<tr>
<td>卡类型</td>
<td>N</td>
<td>2</td>
<td colspan="2">00=贷记 01=借记 02=准贷记</td>
</tr>
<tr>
<td><p>第三方优惠</p>
<p>说明</p></td>
<td>ANS</td>
<td>200</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>第三方平台</td>
<td>ANS</td>
<td>50</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>银商订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>ERP订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2">ERP订单号</td>
</tr>
<tr>
<td>支付方式</td>
<td>N</td>
<td>1</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>查询结果状态</td>
<td>N</td>
<td>1</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>查询结果描述</td>
<td>ANS</td>
<td>50</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B二维码</td>
<td>ANS</td>
<td>200</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B账单时间</td>
<td>ANS</td>
<td>8</td>
<td colspan="2">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B订单状态</td>
<td>ANS</td>
<td>20</td>
<td colspan="2">空格填充</td>
</tr>
</tbody>
</table>

### 有硬件POS通交易

#### 请求参数

<table style="width:99%;">
<caption><p>表 11 无界面调用有硬件POS通交易请求参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 16%" />
<col style="width: 14%" />
<col style="width: 50%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>N</td>
<td>2</td>
<td>01</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>N</td>
<td>2</td>
<td><p>00－消费</p>
<p>01－撤消</p>
<p>02－退货</p>
<p>03－结果查询</p>
<p>04－重打印</p>
<p>05－签到</p>
<p>06－结算</p>
<p>07－重打结算单</p>
<p>40－担保消费</p>
<p>41－担保撤销</p>
<p>42－担保完成</p>
<p>43－担保快速撤销</p>
<p>44－担保快速完成</p></td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>N</td>
<td>12</td>
<td><p>单位为分，不足时左补0</p>
<p>例如：000000000123=1.23元</p></td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>N</td>
<td>8</td>
<td>【退货】时使用，其他空格填充，格式为YYYYMMDD</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>N</td>
<td>12</td>
<td>【退货】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>N</td>
<td>6</td>
<td>【撤销】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>ANS</td>
<td>3</td>
<td>3位随机字符</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>N</td>
<td>50</td>
<td>扫码支付的串码，右补空格</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>N</td>
<td>50</td>
<td>【结果查询】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>N</td>
<td>50</td>
<td>ERP系统生成的唯一交易标识，可用于查询交易和对账使用，建议格式：YYYYMMDDhhmmss+随机数</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>N</td>
<td>32</td>
<td style="text-align: left;">空格填充</td>
</tr>
<tr>
<td style="text-align: center;">无硬件APPIDKEY</td>
<td>ANS</td>
<td>300</td>
<td style="text-align: left;">空格填充</td>
</tr>
</tbody>
</table>

#### 必填要素

<table style="width:99%;">
<caption><p>表 12 无界面调用有硬件POS通常用交易请求参数必填要素</p></caption>
<colgroup>
<col style="width: 13%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">字段</th>
<th>消费</th>
<th>撤销</th>
<th>退货</th>
<th>结果查询</th>
<th>重打印</th>
<th>签到</th>
<th>结算</th>
<th><p>重打</p>
<p>结算单</p></th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>□</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
</tbody>
</table>

|        字段        | 担保消费 | 担保撤销 | 担保完成 | 担保快速撤销 | 担保快速完成 |
|:------------------:|----------|----------|----------|--------------|--------------|
|      应用类型      | ●        | ●        | ●        | ●            | ●            |
|      POS机号       | ○        | ○        | ○        | ○            | ○            |
|     POS员工号      | ○        | ○        | ○        | ○            | ○            |
|    交易类型标志    | ●        | ●        | ●        | ●            | ●            |
|        金额        | ●        | ●        | ●        | ●            | ●            |
|     原交易日期     | ○        | ●        | ●        | ●            | ●            |
|    原交易参考号    | ○        | ○        | ○        | ○            | ○            |
|      原凭证号      | ○        | ●        | ●        | ○            | ○            |
|      LRC校验       | ○        | ○        | ○        | ○            | ○            |
|        串码        | ●        | ○        | ○        | ○            | ○            |
|     银商订单号     | ○        | ○        | ○        | ●            | ●            |
|     ERP订单号      | □        | ○        | ○        | ○            | ○            |
| 无硬件C扫B二维码ID | ○        | ○        | ○        | ○            | ○            |

表 12 无界面调用有硬件POS通担保交易请求参数必填要素

●：必须填入实际值的字段。

○：可随意填的字段，采用空格填充，此字段在该交易中无意义，可能会被打印或被校验。

□：可选字段，该字段在交易中有意义，但填入与否不影响交易成功。

**注：**ERP订单号字段传入可作为后台对账字段使用。

#### 应答参数

<table style="width:99%;">
<caption><p>表 13 无界面调用有硬件POS通交易应答参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 14%" />
<col style="width: 14%" />
<col style="width: 51%" />
<col style="width: 0%" />
</colgroup>
<thead>
<tr>
<th>内容</th>
<th>格式</th>
<th>长度</th>
<th style="text-align: left;">描述</th>
<th style="text-align: left;"></th>
</tr>
</thead>
<tbody>
<tr>
<td>返回码</td>
<td>N</td>
<td>2</td>
<td colspan="2" style="text-align: left;">00表示成功，其他失败</td>
</tr>
<tr>
<td>银行行号</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">支付宝返回[0ZFB]，微信返回[WXZF]，其他返回[POST]</td>
</tr>
<tr>
<td>卡号</td>
<td>ANS</td>
<td>20</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>凭证号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>实付金额</td>
<td>N</td>
<td>12</td>
<td colspan="2" style="text-align: left;">实付金额</td>
</tr>
<tr>
<td>错误说明</td>
<td>ANS</td>
<td>40</td>
<td colspan="2" style="text-align: left;">错误说明中文解释</td>
</tr>
<tr>
<td>商户号</td>
<td>ANS</td>
<td>15</td>
<td colspan="2" style="text-align: left;">商户号</td>
</tr>
<tr>
<td>终端号</td>
<td>ANS</td>
<td>8</td>
<td colspan="2" style="text-align: left;">终端号</td>
</tr>
<tr>
<td>批次号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">批次号</td>
</tr>
<tr>
<td>交易日期</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">交易日期，格式为MMDD</td>
</tr>
<tr>
<td>交易时间</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">交易时间，格式为hhmmss</td>
</tr>
<tr>
<td>交易参考号</td>
<td>ANS</td>
<td>12</td>
<td colspan="2" style="text-align: left;">【退货】交易使用</td>
</tr>
<tr>
<td>授权号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>清算日期</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>LRC校验</td>
<td>ANS</td>
<td>3</td>
<td colspan="2" style="text-align: left;">传入参数中的3位随机字符</td>
</tr>
<tr>
<td>优惠金额</td>
<td>N</td>
<td>12</td>
<td colspan="2" style="text-align: left;">优惠金额=原金额-实付金额</td>
</tr>
<tr>
<td>卡类型</td>
<td>N</td>
<td>2</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td><p>第三方优惠</p>
<p>说明</p></td>
<td>ANS</td>
<td>200</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>第三方平台</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>银商订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>ERP订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">ERP订单号</td>
</tr>
<tr>
<td>支付方式</td>
<td>N</td>
<td>1</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>查询结果状态</td>
<td>N</td>
<td>1</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>查询结果描述</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">【结果查询】返回查询结果描述</td>
</tr>
<tr>
<td>无硬件C扫B二维码</td>
<td>ANS</td>
<td>200</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B账单时间</td>
<td>ANS</td>
<td>8</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B订单状态</td>
<td>ANS</td>
<td>20</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
</tbody>
</table>

### 无硬件POS通交易

#### 请求参数

<table style="width:99%;">
<caption><p>表 14 无界面调用无硬件POS通交易请求参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 16%" />
<col style="width: 14%" />
<col style="width: 50%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">内容</th>
<th>格式</th>
<th>长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>N</td>
<td>2</td>
<td>02</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>ANS</td>
<td>8</td>
<td>ERP系统自定义，不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>N</td>
<td>2</td>
<td><p>00－B2C消费</p>
<p>01－B2C撤消</p>
<p>02－B2C退货</p>
<p>04－B2C结果查询</p>
<p>05－B2C签到</p>
<p>06－B2C结算</p>
<p>31－B2C担保</p>
<p>32－B2C快速担保撤销</p>
<p>33－B2C快速担保完成</p>
<p>10－C2B获取二维码</p>
<p>11－C2B交易查询</p>
<p>12－C2B关闭订单</p>
<p>13－C2B退货</p>
<p>14－C2B申请担保二维码</p>
<p>15－C2B查询担保交易状态</p>
<p>16－C2B担保撤销</p>
<p>17－C2B担保完成</p>
<p>注：ERP系统开发查询接口尽可能避免长款，且结果查询交易必须同时判断[返回码]和[查询结果状态]；</p></td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>N</td>
<td>12</td>
<td><p>单位为分，不足时左补0</p>
<p>例如：000000000123=1.23元</p></td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>N</td>
<td>8</td>
<td>空格填充，格式为YYYYMMDD</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>N</td>
<td>12</td>
<td>空格填充</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>N</td>
<td>6</td>
<td>空格填充</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>ANS</td>
<td>3</td>
<td>3位随机字符</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>N</td>
<td>50</td>
<td>扫码支付的串码，右补空格</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>N</td>
<td>50</td>
<td>【撤销】，【退货】和【查询】等交易使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>N</td>
<td>50</td>
<td>ERP系统生成的唯一交易标识，可用于查询交易和对账使用，建议格式：YYYYMMDDhhmmss+随机数</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>N</td>
<td>32</td>
<td>【C2B关闭订单】时使用，其他空格填充</td>
</tr>
<tr>
<td style="text-align: center;">无硬件APPIDKEY</td>
<td>ANS</td>
<td>300</td>
<td>从平台获取APPID和APPKEY明文后加密并拼接起来，不足右补空格</td>
</tr>
</tbody>
</table>

#### 必填要素

<table style="width:99%;">
<caption><p>表 15 无界面调用无硬件POS通B扫C交易请求参数必填要素</p></caption>
<colgroup>
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
<col style="width: 9%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">字段</th>
<th>B2C消费</th>
<th>B2C撤销</th>
<th>B2C退货</th>
<th>B2C结果查询</th>
<th><p>B2C</p>
<p>签到</p></th>
<th><p>B2C</p>
<p>结算</p></th>
<th><p>B2C</p>
<p>担保</p></th>
<th>B2C快速担保撤销</th>
<th>B2C快速担保完成</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>○</td>
<td>●</td>
<td>●</td>
<td>□</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>□</td>
<td>○</td>
<td>○</td>
<td>□</td>
<td>○</td>
<td>○</td>
<td>□</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
</tbody>
</table>

<table style="width:99%;">
<caption><p>表 16 无界面调用无硬件POS通C扫B交易请求参数必填要素</p></caption>
<colgroup>
<col style="width: 13%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
<col style="width: 10%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">字段</th>
<th><p>C2B</p>
<p>获取二维码</p></th>
<th><p>C2B</p>
<p>交易查询</p></th>
<th><p>C2B</p>
<p>关闭订单</p></th>
<th><p>C2B</p>
<p>退货</p></th>
<th><p>C2B</p>
<p>申请担保二维码</p></th>
<th><p>C2B</p>
<p>查询担保交易状态</p></th>
<th><p>C2B</p>
<p>担保撤销</p></th>
<th><p>C2B</p>
<p>担保完成</p></th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">应用类型</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">POS机号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>●</td>
<td>○</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">原交易日期</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原交易参考号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">原凭证号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">串码</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">银商订单号</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>●</td>
</tr>
<tr>
<td style="text-align: center;">ERP订单号</td>
<td>□</td>
<td>●</td>
<td>○</td>
<td>●</td>
<td>□</td>
<td>●</td>
<td>○</td>
<td>○</td>
</tr>
<tr>
<td style="text-align: center;">无硬件C扫B二维码ID</td>
<td>○</td>
<td>○</td>
<td>●</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
<td>○</td>
</tr>
</tbody>
</table>

●：必须填入实际值的字段。

○：可随意填的字段，采用空格填充，此字段在该交易中无意义，可能会被打印或被校验。

□：可选字段，该字段在交易中有意义，但填入与否不影响交易成功。

**注：**其中银商订单号与ERP订单号在无硬件B扫C的查询交易中，必须传入其中一个，当两个都传入时，以为ERP订单号准；ERP订单号在商户未传入时，由我方程序按后台规则自动生成。

#### 应答参数

<table style="width:99%;">
<caption><p>表 17 无界面调用无硬件POS通交易应答参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 14%" />
<col style="width: 14%" />
<col style="width: 51%" />
<col style="width: 0%" />
</colgroup>
<thead>
<tr>
<th>内容</th>
<th>格式</th>
<th>长度</th>
<th style="text-align: left;">描述</th>
<th style="text-align: left;"></th>
</tr>
</thead>
<tbody>
<tr>
<td>返回码</td>
<td>N</td>
<td>2</td>
<td colspan="2" style="text-align: left;">00表示成功，其他失败</td>
</tr>
<tr>
<td>银行行号</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">支付宝返回[0ZFB]，微信返回[WXZF]，云闪付返回[YLSM]，其他返回空格</td>
</tr>
<tr>
<td>卡号</td>
<td>ANS</td>
<td>20</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>凭证号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>实付金额</td>
<td>N</td>
<td>12</td>
<td colspan="2" style="text-align: left;">【B扫C撤销】返回原交易金额，其他返回实付金额</td>
</tr>
<tr>
<td>错误说明</td>
<td>ANS</td>
<td>40</td>
<td colspan="2" style="text-align: left;">错误说明中文解释</td>
</tr>
<tr>
<td>商户号</td>
<td>ANS</td>
<td>15</td>
<td colspan="2" style="text-align: left;">商户号</td>
</tr>
<tr>
<td>终端号</td>
<td>ANS</td>
<td>8</td>
<td colspan="2" style="text-align: left;">终端号</td>
</tr>
<tr>
<td>批次号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>交易日期</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">交易日期，格式为MMDD</td>
</tr>
<tr>
<td>交易时间</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">交易时间，格式为hhmmss</td>
</tr>
<tr>
<td>交易参考号</td>
<td>ANS</td>
<td>12</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>授权号</td>
<td>N</td>
<td>6</td>
<td colspan="2" style="text-align: left;">【担保】类交易使用</td>
</tr>
<tr>
<td>清算日期</td>
<td>N</td>
<td>4</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>LRC校验</td>
<td>ANS</td>
<td>3</td>
<td colspan="2" style="text-align: left;">传入参数中的3位随机字符</td>
</tr>
<tr>
<td>优惠金额</td>
<td>N</td>
<td>12</td>
<td colspan="2" style="text-align: left;">优惠金额=原金额-实付金额</td>
</tr>
<tr>
<td>卡类型</td>
<td>N</td>
<td>2</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td><p>第三方优惠</p>
<p>说明</p></td>
<td>ANS</td>
<td>200</td>
<td colspan="2" style="text-align: left;">第三方优惠说明</td>
</tr>
<tr>
<td>第三方平台</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">第三方平台</td>
</tr>
<tr>
<td>银商订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">银商订单号</td>
</tr>
<tr>
<td>ERP订单号</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">ERP订单号</td>
</tr>
<tr>
<td>支付方式</td>
<td>N</td>
<td>1</td>
<td colspan="2" style="text-align: left;"><p>0=支付宝</p>
<p>1=微信</p>
<p>2=银联钱包</p>
<p>3=其他</p></td>
</tr>
<tr>
<td>查询结果状态</td>
<td>N</td>
<td>1</td>
<td colspan="2" style="text-align: left;"><p>【结果查询】返回</p>
<p>0=成功</p>
<p>1=超时</p>
<p>2=已撤销</p>
<p>3=已退货</p>
<p>4=已冲正</p>
<p>5=失败</p>
<p>其他=未知错误</p></td>
</tr>
<tr>
<td>查询结果描述</td>
<td>ANS</td>
<td>50</td>
<td colspan="2" style="text-align: left;">空格填充</td>
</tr>
<tr>
<td>无硬件C扫B二维码</td>
<td>ANS</td>
<td>200</td>
<td colspan="2" style="text-align: left;">二维码URL</td>
</tr>
<tr>
<td>无硬件C扫B账单时间</td>
<td>ANS</td>
<td>8</td>
<td colspan="2" style="text-align: left;">账单时间</td>
</tr>
<tr>
<td>无硬件C扫B订单状态</td>
<td>ANS</td>
<td>20</td>
<td colspan="2" style="text-align: left;">订单状态</td>
</tr>
</tbody>
</table>

### 传入及传出参实例

#### 银行卡消费传入参数

“0012345678234567890000000000000120141212123412341234123456000 ”

表示：

‘应用类型标志’= \[00\]

‘POS机号’ = \[12345678\]

‘POS员工号’ = \[23456789\]

‘交易类型标志’= \[00\]

‘金额’= \[000000000001\]

‘原交易日期’= \[20141212\]

‘原交易参考号’= \[123412341234\]

‘原凭证号’= \[123456\]

‘LRC校验’= \[000\]

‘POS通串码’=\[ \]

‘银商订单号’=\[ \]

‘ERP订单号’=\[ \]

‘无硬件C扫B二维码ID’=\[ \]。

#### 银行卡消费成功传出参数

“009088622848\*\*\*\*\*\*\*\*\*8812 003420000000000001交易成功 123456789900089123450890003101230102801000120402332 123000000000000000001 ”

表示：

‘返回码’= \[00\]

‘银行行号’= \[9088\]

‘卡号’= \[622848\*\*\*\*\*\*\*\*\*8812 \]

‘凭证号’= \[003420\]

‘金额’= \[000000000001\]

‘错误说明’=\[交易成功 \]

‘商户号’= \[123456789900089\]

‘终端号’= \[12345089\]

‘批次号’= \[000310\]

‘交易日期’= \[1230\]

‘交易时间’= \[102801\]

‘交易参考号’= \[000120402332\]

‘授权号’= \[ \]

‘清算日期’= \[1230\]

‘LRC’= \[000\]

‘优惠金额’=\[000000000000\]

‘卡类型’=\[01\]

‘第三方优惠说明’=\[ \]

‘银商订单号’=\[ \]

‘ERP订单号’=\[ \]

‘支付方式’=\[ \]

‘查询结果’=\[ \]

‘查询结果描述’=\[ \]

‘无硬件C扫B二维码’=\[ \]

‘无硬件C扫B账单时间’=\[ \]

‘无硬件C扫B订单状态’=\[ \]。

#### 银行卡签到传入参数

“0012345678234567890500000000000120141212123412341234123456000 ”

#### 银行卡签到成功传出参数

“00 交易成功 000 ”

#### 有硬件POS通消费传入参数

“0112345678123456780000000000000120120625123456789012123456888135169359544058827 ”

表示：

‘应用类型标志’= \[01\]

‘POS机号’ = \[12345678\]

‘POS员工号’ = \[12345678\]

‘交易类型标志’= \[00\]

‘‘金额’= \[000000000001\]

‘原交易日期’= \[20120625\]

‘原交易参考号’= \[123456789012\]

‘原凭证号’= \[123456\]

‘LRC校验’= \[888\]

‘POS通串码’=\[135169359544058827 \]

‘银商订单号’=\[ \]

‘ERP订单号’=\[ \]

‘无硬件C扫B二维码ID’=\[ \]。

#### 有硬件POS通消费传出参数

“00WXZF135169\*\*\*\*\*\*\*\*\*\*\*\*\*\*711274000000000001交易成功 103290070121511123415110001180719170824900031756875 0719888000000000000 ”

表示：

‘返回码’= \[00\]

‘银行行号’= \[WXZF\]

‘卡号’= \[135169\*\*\*\*\*\*\*\*\*\*\*\*\*\*\]

‘凭证号’= \[711274\]

‘金额’= \[000000000001\]

‘错误说明’=\[交易成功 \]

‘商户号’= \[103290070121511\]

‘终端号’= \[12341511\]

‘批次号’= \[000118\]

‘交易日期’= \[0719\]

‘交易时间’= \[170824\]

‘交易参考号’= \[900031756875\]

‘授权号’= \[ \]

‘清算日期’= \[0719\]

‘LRC’= \[888\]

‘优惠金额’=\[000000000000\]

‘卡类型’=\[ \]

‘第三方优惠说明’=\[ \]

‘银商订单号’=\[ \]

‘ERP订单号’=\[ \]

‘支付方式’=\[ \]

‘查询结果’=\[ \]

‘查询结果描述’=\[ \]

‘无硬件C扫B二维码’=\[ \]

‘无硬件C扫B账单时间’=\[ \]

‘无硬件C扫B订单状态’=\[ \]。

#### 有硬件POS通签到传入参数

“0112345678234567890500000000000120141212123412341234123456000 ”

#### 有硬件POS通签到传出参数

“00 交易成功 000 ”

#### 无硬件POS通(B2C)消费传入参数

“0212345678234567890000000000000120141212123412341234123456000134512234146671358 ”

表示：

‘应用类型标志’= \[02\]

‘POS机号’ = \[12345678\]

‘POS员工号’ = \[23456789\]

‘交易类型标志’= \[00\]

‘金额’= \[000000000001\]

‘原交易日期’= \[20141212\]

‘原交易参考号’= \[123412341234\]

‘原凭证号’= \[123456\]

‘LRC校验’= \[000\]

‘POS通串码’=\[134512234146671358 \]

‘银商订单号’=\[ \]

‘ERP订单号’=\[ \]

‘无硬件C扫B二维码ID’=\[ \]。

#### 无硬件POS通(B2C)消费传出参数

“00WXZF 87872000000000000110000成功响应码 89853017299093153103639 123014225112917451130W 000000000000000 微信钱包支付0.01元 微信钱包 20191230142245012917451130 201912301422436800262031741417 1 ”

表示：

‘返回码’= \[00\]

‘银行行号’= \[WXZF\]

‘卡号’= \[ \]

‘凭证号’= \[878720\]

‘金额’= \[000000000001\]

‘错误说明’=\[10000成功响应码 \]

‘商户号’= \[898530172990931\]

‘终端号’= \[53103639\]

‘批次号’= \[ \]

‘交易日期’= \[1230\]

‘交易时间’= \[142251\]

‘交易参考号’= \[12917451130W\]

‘授权号’= \[ \]

‘清算日期’= \[ \]

‘LRC’= \[000\]

‘优惠金额’=\[000000000000\]

‘卡类型’=\[ \]

‘第三方优惠说明’=\[微信钱包支付0.01元 \]

‘第三方平台’=\[微信钱包 \]

‘银商订单号’==\[20191230142245012917451130 \]

‘ERP订单号’=\[201912301422436800262031741417 \]

‘支付方式’=\[1\]

‘查询结果’=\[ \]

‘查询结果描述’=\[ \]

‘无硬件C扫B二维码’=\[ \]

‘无硬件C扫B账单时间’=\[ \]

‘无硬件C扫B订单状态’=\[ \]。

## 有界面调用

### 交易函数

<table style="width:98%;">
<caption><p>表 13 有界面exe调用模式交易函数说明表</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 80%" />
</colgroup>
<thead>
<tr>
<th>exe名称</th>
<th><blockquote>
<p>gmc.exe</p>
</blockquote></th>
</tr>
</thead>
<tbody>
<tr>
<td>功能描述</td>
<td><blockquote>
<p>交易执行函数</p>
</blockquote></td>
</tr>
<tr>
<td>入参</td>
<td><blockquote>
<p>传入定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>出参</td>
<td><blockquote>
<p>传出定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>说明</td>
<td><blockquote>
<p>有界面EXE独立调用方式需调用方调用gmc.exe启用我方提供的操作界面供操作员使用，所有交易均通过该操作界面完成。</p>
<p>有界面EXE文件传递与前一种方法类似，但需调用方在调用gmc.exe前，主动地在同级目录下生成一个request.txt文件作为gmc.exe交易的传入参数，随后调起gmc.exe，所有交易均通过传入参数控制，无需用户对gmc.exe界面进行操作。交易完成后，gmc.exe将应答参数生成到同目录下的response.txt中。</p>
</blockquote></td>
</tr>
</tbody>
</table>

<table style="width:98%;">
<caption><p>表 14 有界面dll调用模式交易函数说明表</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 80%" />
</colgroup>
<thead>
<tr>
<th>dll名称</th>
<th><blockquote>
<p>bankpos.dll</p>
</blockquote></th>
</tr>
</thead>
<tbody>
<tr>
<td>函数名</td>
<td><blockquote>
<p>int mispos(char * request,char *response)</p>
</blockquote></td>
</tr>
<tr>
<td>功能描述</td>
<td><blockquote>
<p>交易执行函数</p>
</blockquote></td>
</tr>
<tr>
<td>请求参数</td>
<td><blockquote>
<p>传入定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>应答参数</td>
<td><blockquote>
<p>传出定长字符串，具体格式请参见下文表格</p>
</blockquote></td>
</tr>
<tr>
<td>说明</td>
<td><blockquote>
<p>有界面DLL调用方式中，被调用的函数mispos被封装在bankpos.dll中，调用方调用该函数时，DLL通过txt文件与gmc.exe进行传入和传出参数的交互。这一过程中，尽管用户可看到gmc.exe的操作界面，但交易流程由传入参数进行控制，无需用户对gmc.exe界面进行操作。</p>
</blockquote></td>
</tr>
</tbody>
</table>

### 请求参数

<table style="width:98%;">
<caption><p>表 15 无界面调用请求参数</p></caption>
<colgroup>
<col style="width: 17%" />
<col style="width: 14%" />
<col style="width: 14%" />
<col style="width: 51%" />
</colgroup>
<thead>
<tr>
<th style="text-align: center;">内容</th>
<th style="text-align: center;">格式</th>
<th style="text-align: center;">长度</th>
<th>描述</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: center;">POS机号</td>
<td style="text-align: center;">ANS</td>
<td style="text-align: center;">15</td>
<td>不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">POS员工号</td>
<td style="text-align: center;">ANS</td>
<td style="text-align: center;">15</td>
<td>不足右补空格</td>
</tr>
<tr>
<td style="text-align: center;">交易类型标志</td>
<td style="text-align: center;">AN</td>
<td style="text-align: center;">1</td>
<td><p>为'C'，表示是正数（消费交易）；</p>
<p>为'D'，表示是负数（撤销交易）；</p>
<p>为'R'，表示是负数（退货交易）；</p>
<p>为'I'，是查余交易；</p>
<p>为'E'，管理类交易(签到、重打印、结算等)</p></td>
</tr>
<tr>
<td style="text-align: center;">金额</td>
<td style="text-align: center;">N</td>
<td style="text-align: center;">12</td>
<td>信用卡消费金额，char(12)，没有小数点"."，精确到分，最后两位为小数位，不足左补0。</td>
</tr>
<tr>
<td style="text-align: center;">备用</td>
<td style="text-align: center;">ANS</td>
<td style="text-align: center;">6</td>
<td>“000000”，备用（针对预售权类交易有效）</td>
</tr>
<tr>
<td style="text-align: center;">LRC校验</td>
<td style="text-align: center;">ANS</td>
<td style="text-align: center;">3</td>
<td>3位随机数字</td>
</tr>
</tbody>
</table>

### 应答参数

| 内容 | 格式 | 长度 | 描述 |
|:--:|:--:|:--:|----|
| 响应码 | N | 2 | 00 表示成功，其它表示失败 |
| 金额 | N | 12 | 信用卡刷卡成功金额，char(12)，没有小数点"."，精确到分，最后两位为小数位，不足前补0。 |
| 卡号 | N | 19 | 不足右补空格 |
| 备用 | ANS | 19 | 备用 |
| 卡类型标志 | N | 4 | 银行卡卡类 |
| 发卡行名称 | ANS | 8 | 银行卡所属银行名称 |
| LRC校验 | ANS | 3 | 三位数字，应该和请求一致 |
| 商户号 | N | 15 |  |
| 终端号 | N | 8 |  |
| 批次号 | N | 6 |  |
| 流水号 | N | 6 |  |
| 系统参考号 | N | 12 |  |
| 日期 | N | 4 | MMDD |
| 时间 | N | 6 | hhmmss |

表 16 无界面调用应答参数

### 传入及传出实例

#### 消费传入参数

“111112222233333444445555566666C000000000001123456000”

表示：

‘POS机号’ = \[111112222233333\]

‘POS员工号’= \[444445555566666\]

‘交易类型标志’= \[C\]

‘金额’= \[000000000001\]

‘备用’= \[123456\]

‘LRC校验’= \[000\]。

#### 消费成功传出参数

“000000000000016228481610118721310 9088 000123456789111115111150050005730002530001083138060909161551”

表示：

‘响应码’= \[00\]

‘金额’= \[00000000000001\]

‘卡号’= \[6228481610118721310\]

‘备用’= \[ \]

‘卡类型’= \[9088\]

‘发卡行名称’= \[ \]

‘LRC校验’= \[000\]

‘商户号’= \[123456789111115\]

‘终端号’= \[11115005\]

‘批次号’= \[000573\]

‘凭证号’= \[000253\]

‘系统参考号’= \[000108313806\]

‘日期’= \[0909\]

‘时间’= \[161551\]。

## 部分开发案例

### Delphi调用银行卡交易动态库（posinf.dll）

首先整体的界面如下所示。

<figure>
<img src="./04-mis-pos-media/media/image3.png" />
<figcaption><p>图 2Delphi调用银行卡交易动态库(posinf.dll）</p></figcaption>
</figure>

代码的步骤说明如下所示。第一步：声明bankall函数，加载动态库posinf.dll。

![](./04-mis-pos-media/media/image4.png)

第二步：定义入参，出参的结构体。

![](./04-mis-pos-media/media/image5.png)

![](./04-mis-pos-media/media/image6.png)

第三步：调用bankall函数。

![](./04-mis-pos-media/media/image7.png)

![](./04-mis-pos-media/media/image8.png)

![](./04-mis-pos-media/media/image9.png)

### IE调用银行卡交易控件（OCX）（posinf.dll）

首先整体的界面如下所示。

![](./04-mis-pos-media/media/image10.png)

图 3IE调用银行卡交易控件(OCX)(posinf.dll)

代码的步骤如下所示。第一步：关联ClASSID.

![](./04-mis-pos-media/media/image11.png)

第二步：定义变量DUmsocx1。

![](./04-mis-pos-media/media/image12.png)

第二步：调用trans()函数。

![](./04-mis-pos-media/media/image13.png)

其中，对于trans()函数的输入参数为Dumsocx1.bankrequest，输出参数为Dumsocx1.bankResponse。其中输入参数中各个id的解释如下所示：

![](./04-mis-pos-media/media/image14.png)

### C#调用银行卡交易控件（posinf.dll）

首先整体的界面如下图所示。

![](./04-mis-pos-media/media/image15.jpeg)

图 4 C#调用银行卡交易控件（posinf.dll）

代码说明如下。第一步：声明bankall函数，加载动态库posInf.dll。

![](./04-mis-pos-media/media/image16.png)

第二步：定义输入结构体。

![](./04-mis-pos-media/media/image17.png)

第三步：调用函数bankall。

![](./04-mis-pos-media/media/image18.png)

### VC调用银行卡交易控件（posinf.dll）

首先整体的界面如下图所示。

<figure>
<img src="./04-mis-pos-media/media/image19.png" />
<figcaption><p>图 5VC调用银行卡交易控件（posinf.dll）</p></figcaption>
</figure>

代码步骤说明如下。第1步：初始化输入参数。

![](./04-mis-pos-media/media/image20.png)

![](./04-mis-pos-media/media/image21.png)

第二步：加载动态库posinf.dll，并且调用函数bankall。

![](./04-mis-pos-media/media/image22.png)

### Java调用银行卡交易控件（posinf.dll）

首先界面如下图所示。

![](./04-mis-pos-media/media/image23.png)

图 6Java调用银行卡交易控件（posinf.dll）

代码说明如下。第一步：定义输入类，输出类。输入类由8个字符串变量构成，表示了包括收银机号、操作员号、交易类型等在内的8个字段。输出类由15个字符串变量构成，输出类中变量i代表汉字的个数。

![](./04-mis-pos-media/media/image24.png)

![](./04-mis-pos-media/media/image25.png)

第二步：加载动态库posInf.dll。我们采用JNA的调用方式。

![](./04-mis-pos-media/media/image26.png)

第三步：调用bankall函数。

![](./04-mis-pos-media/media/image27.png)

### Delphi调用银行卡交易动态库实例（bankpos.dll）

首先整体的界面如下所示。

<figure>
<img src="./04-mis-pos-media/media/image28.png" />
<figcaption><p>图 7Delphi调用银行卡交易动态库实例（bankpos.dll）</p></figcaption>
</figure>

代码步骤如下所示。第一步：加载动态库bankpos.dll，并指明调用函数。

![](./04-mis-pos-media/media/image29.png)

第二步：定义入参，出参结构体。

![](./04-mis-pos-media/media/image30.png)

第三步：定义变量，调用函数bankall。

![](./04-mis-pos-media/media/image31.png)

# MIS-POS系统的操作及使用

## 参数配置

gmc目录下专门有一个为方便进行参数配置而开发的工具——MisPosCfg，双击MisPosCfg.exe，登录进入配置界面即可进行相关配置。该配置工具提供银行卡、有硬件POS通和无硬件POS通三种标准应用的通用配置，另外还新增一个扩展应用的参数配置功能，总共可以同时满足四种应用的参数配置。

### 银行卡、POS通的参数配置

银行卡、有硬件POS通的参数配置在大体上是相同的，本节将合并说明这两个应用的相关配置。银行卡、有硬件POS通都会用到如下参数项：EMV串口指令版本、商户参数、系统参数、通讯参数、打印参数。

<figure>
<img src="./04-mis-pos-media/media/image32.png" />
<figcaption><p>图 8 银行卡及有硬件POS通商户参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image33.png" />
<figcaption><p>图 9 银行卡及有硬件POS通系统参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image34.png" />
<figcaption><p>图 10 银行卡及有硬件POS通通讯参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image35.png" />
<figcaption><p>图 11 银行卡及有硬件POS通打印参数</p></figcaption>
</figure>

说明：

图中打\*的参数项为必填项，用户在使用gmc时对必填项进行了正确的配置之后，才能正常使用gmc；

\<串口号\>：用户需正确设置设备的串口号，可通过如下方法该号查看：首先右击“我的电脑”，选择“管理”，在出现的“计算机管理”界面中左侧位置找到“设备管理器”并点击进入。（win8以上的系统直接右击“开始”或按“win+x”，在弹出的菜单中选择“设备管理器”即可）。然后在设备中找到“端口(COM和LPT)”，点选后会展开名为“通信端口(COMx)”的设备，通常该数字x即为设备的串口号。如，例图中显示为“通信端口(COM 1)”，则说明设备串口号为1。

<figure>
<img src="./04-mis-pos-media/media/image36.png" />
<figcaption><p>图 12 查看串口号</p></figcaption>
</figure>

\<备用通讯地址序列\>可以添加和删除备用通讯地址，在\<备用IP\>和\<备用Client\>中分别填写备用通讯的IP和端口，然后点击“添加”按钮即可添加到备用通讯地址序列中去，点“删除”按钮会将删除\<备用通讯地址序列\>当前显示的地址和端口。

\<键盘POS类型\>提供三种设置，其中，选择“0--EMV键盘”适用于gmc或ERP控制打印机来打印签购单，选择“1--Compos机(模版打印)”则Compos机使用gmc的模版来打印签购单，选择“2--Compos机(非模版打印)”则Compos机使用pos机本身的模板来打印签购单。

\<打印模式\>提供5种模式可供打印选择，仅当\<键盘POS类型\>选择“0--EMV键盘”，gmc或ERP控制打印机来打印签购单时，\<打印模式\>的相关设置才会有效。

\<打印端口\>的相关配置当\<打印模式\>选择“2--串口打印”或“3--并口打印”时才会有效。

### 无硬件POS通的参数配置

无硬件POS通的参数配置主要也包括商户参数、系统参数、通讯参数、打印参数四大部分，每部分参数均包含“B扫C”和“C扫B”，与银行卡、有硬件POS通的参数略有不同。

<figure>
<img src="./04-mis-pos-media/media/image37.png" />
<figcaption><p>图 13无硬件POS通商户参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image38.png" />
<figcaption><p>图 14无硬件POS通系统参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image39.png" />
<figcaption><p>图 15无硬件POS通通讯参数</p></figcaption>
</figure>

<figure>
<img src="./04-mis-pos-media/media/image40.png" />
<figcaption><p>图 16无硬件POS通打印参数</p></figcaption>
</figure>

说明：

图中打\*的参数项为必填项，用户在使用gmc中无硬件POS通功能时对必填项进行了正确的配置之后，才能正常使用；

\<APPID密文\>和\<APPKEY密文\>是针对B扫C业务的，开通B扫C业务时必须向开放平台申请APPID和APPKEY并绑定B扫C商户号，然后经MIS室加密，将加密后的密文对应配置到\<APPID密文\>和\<APPKEY密文\>处。

### 新增应用的参数配置

MisPosCfg配置工具除了银行卡、有硬件POS通、无硬件POS通三大常规应用之外，还额外扩展了一个新增应用的相关配置。在gmc下的main.ini配置文件中配置新增一个应用，MisPosCfg配置工具界面上则会显示该新增应用的配置界面，然后按照本本章第1节的配置操作方法进行配置即可完成新增应用的相关配置。

main.ini配置文件新增应用配置如下：

![](./04-mis-pos-media/media/image41.png)

MisPosCfg配置工具新增应用配置界面如下：

<figure>
<img src="./04-mis-pos-media/media/image42.png" />
<figcaption><p>图 17MisPosCfg配置工具新增应用配置界面</p></figcaption>
</figure>

## 测试工具（Demo）使用

针对前文所述的三种调用方式，我们提供各类测试工具与之对应，分别为：无界面dll调用测试Demo、有gmc界面独立运行或文件传递测试Demo、无界面ocx调用测试Demo。

### 无界面dll调用

用户从\<应用类型\>的下拉框中选择应用类型，从\<交易类型\>的下拉框中选择交易类型，填好相应交易类型中的必要参数后，点击“确认”按钮，即可进行交易。图中用红色字体显示的参数均为必要参数，非必要参数保持默认值即可。本节以银行卡交易为例进行说明。

#### 消费

<figure>
<img src="./04-mis-pos-media/media/image43.png" />
<figcaption><p>图 18无界面dll调用Demo消费界面</p></figcaption>
</figure>

#### 撤销

<figure>
<img src="./04-mis-pos-media/media/image44.png" />
<figcaption><p>图 19无界面dll调用Demo撤销界面</p></figcaption>
</figure>

#### 退货

<figure>
<img src="./04-mis-pos-media/media/image45.png" />
<figcaption><p>图 20无界面dll调用Demo退货界面</p></figcaption>
</figure>

#### 查余

<figure>
<img src="./04-mis-pos-media/media/image46.png" />
<figcaption><p>图 21无界面dll调用Demo查余界面</p></figcaption>
</figure>

#### 重打印

<figure>
<img src="./04-mis-pos-media/media/image47.png" />
<figcaption><p>图 22无界面dll调用Demo重打印</p></figcaption>
</figure>

#### 签到

<figure>
<img src="./04-mis-pos-media/media/image48.png" />
<figcaption><p>图 23无界面dll调用Demo签到界面</p></figcaption>
</figure>

#### 结算

<figure>
<img src="./04-mis-pos-media/media/image49.png" />
<figcaption><p>图 24无界面dll调用Demo结算界面</p></figcaption>
</figure>

#### 重打结算单

<figure>
<img src="./04-mis-pos-media/media/image50.png" />
<figcaption><p>图 25 重打结算单</p></figcaption>
</figure>

### 有界面dll调用

（1）运行gmc文件夹中的gmc.exe即可启动该测试工具：

<figure>
<img src="./04-mis-pos-media/media/image51.png" />
<figcaption><p>图 26 gmc.exe的应用类型界面</p></figcaption>
</figure>

（2）选择应用类型进入交易类型操作界面：

<figure>
<img src="./04-mis-pos-media/media/image52.png" />
<figcaption><p>图 27 gmc.exe的交易类型选择界面</p></figcaption>
</figure>

用户可根据需要点击不同按钮进行相应的交易，包括\[消费\]、\[查余\]、\[签到\]、\[撤销\]、\[退货\]、\[分期消费\]、\[分期撤销\]等，点击后会产生相应的交易流程。

点击\[1 消费\]后，输入交易金额即可开始进行消费交易。

点击\[2 查余\]后，根据密码键盘上的提示进行相应操作即可。

点击\[3 签到\]后，等待密码键盘的反馈信息即可。

点击\[4 管理类交易\]后会进入相应的子界面，可进行查询、签到、打印等管理类操作，如下图所示。

点击\[5 撤销\]后，系统提示用户输入需要撤销交易的金额以及凭证号，正确输入后，POS机端会要求用户确认撤销，并刷卡验证，完成该过程则可成功撤销。

点击\[6 退货\]后，系统会对用户进行身份验证。用户正确输入主管号和主管密码后进入退货信息输入界面，这时需要用户输入退货金额、系统参考号以及原交易日期，正确输入各项信息后，根据POS机端的提示进行操作即可完成退货。

<figure>
<img src="./04-mis-pos-media/media/image53.png" />
<figcaption><p>图 28 gmc.exe管理类交易类型选择界面</p></figcaption>
</figure>

### 无界面ocx调用

#### 加载ocx控件 

用户在使用ocx调用时，应先在自己的计算机中进行注册。注册方法如下：

步骤一：右击gmc下backup文件夹中的“RegOcx.bat”，选择“以管理员身份运行”，之后会收到提示表明已经注册成功。

步骤二：接下来用户只需双击运行gmc文件夹中的“CallOcx.htm”即可启动测试工具，值得注意的是，目前该工具仅支持IE浏览器和部分IE内核的浏览器，为可靠起见，我们建议用户通过IE浏览器启动该测试工具，其方法是右击 “CallOcx.htm”，选中“打开方式”中的“Internet Explorer”。

（附注：目前IE内核的浏览器包括：猎豹安全浏览器IE兼容模式、蚂蚁浏览器、QQ浏览器7、爱帆浏览器、360安全浏览器、搜狗浏览器兼容模式、瑞影浏览器、极速云浏览器、360极速浏览器兼容模式、百度浏览器兼容模式。）

步骤三：点击页面顶部的安全提示，选择“允许阻止的内容”并在接下来的弹窗提示中均选择“是”，即可成功注册。

<figure>
<img src="./04-mis-pos-media/media/image54.png" />
<figcaption><p>图 29 ocx调用测试demo界面</p></figcaption>
</figure>

#### jsp调用ocx不成功的解决方案

若用户遇到jsp调用ocx不成功的情况，可尝试以下解决方案：

1.Internet选项--安全--可信站点--站点--添加网址--勾选“对该区域中的所有站点要求服务器验证”；

2.Internet选项--安全--可信站点--默认级别--设置为最低

3.Internet选项--安全--可信站点--自定义级别：启用“对未标记为可安全执行脚本的ActiveX控件初始化并执行脚本”和“下载未签名的ActiveX控件”；

4.控件注册和注销用系统管理员权限运行且运行IE使用管理员身份；

5.控制面板--用户账户--更改用户账户控制设置--从不通知

6.打开工具--Internet选项--高级 勾选“允许活动内容在我的计算机上的文件中运行”

7.注册ocx控件采用绝对路径regsvr32 -v C:\gmc\umsocx.ocx；

8.管理员身份打开cmd.exe，在运行注册ocx。

2.4 文件传递

运行gmc下backup文件夹中的Project1_文件传递.exe即可启动文件传递测试工具：

<figure>
<img src="./04-mis-pos-media/media/image55.png" />
<figcaption><p>图 30文件传递测试</p></figcaption>
</figure>

## 常见简单错误及解决思路

这里简单给出一些在开发及测试过程中会遇到的常见错误及解决方法，更多错误码说明及处理方法，请登录MIS知识库平台查询，MIS室知识库平台网址：<http://144.131.254.103:9876/umskl/index.html>。

若按照以上操作仍无法解决问题，请提供日志文件（默认umsgmc.log）给总公司技术人员。

| 错误码 | 错误说明           | 解决方法                                         |
|:------:|:-------------------|:-------------------------------------------------|
|   Y5   | 与密码键盘通讯失败 | 在pos.ini和pos机上设置串口号,波特率和奇校验      |
|   Y3   | 读取配置文件失败   | 检查程序路径是否有问题                           |
|   YY   | 版本校验错         | 在pos机上查看商户号来源,使用正确商户号加密生成sn |
|   Y8   | 网络故障           | telnet ip和端口                                  |

表 17常见简单错误及解决思路

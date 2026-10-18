using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web;
using System.Windows.Forms;
using Newtonsoft.Json.Linq;

namespace UnionPayPlugin;

public class MainForm : Form
{
	private string _SystemName1 = "银联";

	private string _SystemName2 = "接口插件";

	private int _AppRunLag = 0;

	private Thread thd_HttpListen;

	private HttpListener listerner = new HttpListener();

	private string _HttpServerUrl = "";

	private string _InterfaceCode = "3";

	private string _TransMode = "1";

	private IContainer components = null;

	private Label label4;

	private Label label2;

	private Label label3;

	private PictureBox pbTop;

	private Label lblProduct;

	private Label lblVersion;

	private NotifyIcon nIcon;

	private ContextMenuStrip cmsRightMenu;

	private ToolStripMenuItem tsmiExitSys;

	private System.Windows.Forms.Timer timerClearMom;

	private ToolStripMenuItem tsmiOpenSys;

	public MainForm()
	{
		InitializeComponent();
		CLibWin.WriteToLog("【系统】启动...");
		CLibWin.WriteToLog("【版本号】v" + Application.ProductVersion);
		try
		{
			if (!Fun.CollapseRunTest())
			{
				CLibWin.WriteToLog("【" + base.Name + ".cs 行号 " + Fun.GetCodeNum() + "】【错误描述】:[启动]程序可以运行,但发生异常时可能会导致程序崩溃.请安装:dotNetFx40LP_Client_x86_x64zh-Hans-Languagepack.exe(.Net4.0语言包)");
				FormMessage frm = new FormMessage();
				frm.ThisShow("提示", "[启动]程序可以运行,但发生异常时可能会导致程序崩溃.请安装:\r\n dotNetFx40LP_Client_x86_x64zh-Hans-Languagepack.exe(.Net4.0语言包)");
				frm.ShowDialog();
			}
			CLibWin.WriteToLog("[启动]加载本地配置...");
			string msg = GetLocalConfig();
			if (msg != "")
			{
				MessageBox.Show(msg);
				Environment.Exit(0);
				return;
			}
			CLibWin.WriteToLog("[启动]加载本地配置完毕");
			Text = _SystemName1 + "-" + _SystemName2 + " v" + Application.ProductVersion;
			nIcon.Text = _SystemName1 + "-" + _SystemName2;
			nIcon.BalloonTipTitle = _SystemName1 + "-" + _SystemName2;
			nIcon.BalloonTipText = _SystemName1 + "-" + _SystemName2;
			lblProduct.Text = _SystemName2;
			lblVersion.Text = Application.ProductVersion;
			if (_AppRunLag > 0)
			{
				CLibWin.WriteToLog("[启动]开始延迟(" + _AppRunLag + ")秒启动...");
				Thread.Sleep(_AppRunLag * 1000);
			}
			CLibWin.WriteToLog("[启动]开启监听...");
			msg = OpenHttpListen();
			if (msg != "")
			{
				CLibWin.WriteToLog("[启动]监听开启失败,请检查配置,端口是否冲突或给予管理员权限," + msg);
				MessageBox.Show("[启动]监听开启失败,请检查配置,端口是否冲突或给予管理员权限," + msg);
				Environment.Exit(0);
			}
			else
			{
				CLibWin.WriteToLog("[启动]开启监听完毕");
				CLibWin.WriteToLog("【系统】启动完毕");
			}
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
			Environment.Exit(0);
		}
	}

	private void nIcon_MouseDoubleClick(object sender, MouseEventArgs e)
	{
		Show();
		base.WindowState = FormWindowState.Normal;
		Activate();
		base.ShowInTaskbar = true;
	}

	private void tsmiOpenSys_Click(object sender, EventArgs e)
	{
		Show();
		base.WindowState = FormWindowState.Normal;
		Activate();
		base.ShowInTaskbar = true;
	}

	private void tsmiExitSys_Click(object sender, EventArgs e)
	{
		try
		{
			MessageBoxButtons messButton = MessageBoxButtons.OKCancel;
			DialogResult dr = MessageBox.Show("确定退出系统吗?", "提示", messButton, MessageBoxIcon.Exclamation);
			if (dr == DialogResult.OK)
			{
				CLibWin.WriteToLog("【系统】退出...");
				if (thd_HttpListen != null && thd_HttpListen.IsAlive)
				{
					try
					{
						thd_HttpListen.Abort();
					}
					catch
					{
					}
				}
				Thread.Sleep(2);
				try
				{
					if (thd_HttpListen.IsAlive)
					{
						thd_HttpListen.Abort();
					}
				}
				catch
				{
				}
				CLibWin.WriteToLog("【系统】退出");
			}
		}
		catch
		{
		}
		Environment.Exit(0);
	}

	private void MainForm_FormClosing(object sender, FormClosingEventArgs e)
	{
		e.Cancel = true;
		Hide();
		base.ShowInTaskbar = false;
	}

	private void MainForm_SizeChanged(object sender, EventArgs e)
	{
		if (base.WindowState == FormWindowState.Minimized)
		{
			base.ShowInTaskbar = false;
		}
	}

	private string GetLocalConfig()
	{
		try
		{
			CLibWin.WriteToLog("[启动]加载本地参数");
			CLibWin.SetIniFilePath(Application.StartupPath + "\\config.ini");
			_SystemName1 = CLibWin.ReadIniFile("MAIN", "SystemName1");
			if (_SystemName1.Trim() == "")
			{
				CLibWin.WriteToLog("[配置]SystemName1:" + _SystemName1.ToString() + ",错误,系统名称1(主标题)不能为空");
				return "[配置]SystemName1:" + _SystemName1.ToString() + ",错误,系统名称1(主标题)不能为空";
			}
			CLibWin.WriteToLog("[配置]SystemName1:" + _SystemName1.ToString());
			_SystemName2 = CLibWin.ReadIniFile("MAIN", "SystemName2");
			if (_SystemName2.Trim() == "")
			{
				CLibWin.WriteToLog("[配置]SystemName2:" + _SystemName2.ToString() + ",错误,系统名称2(副标题)不能为空");
				return "[配置]SystemName2:" + _SystemName2.ToString() + ",错误,系统名称2(副标题)不能为空";
			}
			CLibWin.WriteToLog("[配置]SystemName2:" + _SystemName2.ToString());
			string apprunlag = CLibWin.ReadIniFile("MAIN", "AppRunLag");
			if (!int.TryParse(apprunlag, out _AppRunLag))
			{
				CLibWin.WriteToLog("[配置]AppRunLag:" + _AppRunLag + ",错误,延迟启动时间必须是数字");
				return "[配置]AppRunLag:" + _AppRunLag + ",错误,延迟启动时间必须是数字";
			}
			if (_AppRunLag < 0)
			{
				CLibWin.WriteToLog("[配置]AppRunLag:" + _AppRunLag + ",错误,延迟启动时间不能小于0");
				return "[配置]AppRunLag:" + _AppRunLag + ",错误,延迟启动时间不能小于0";
			}
			CLibWin.WriteToLog("[配置]AppRunLag:" + _AppRunLag);
			_HttpServerUrl = CLibWin.ReadIniFile("MAIN", "HttpServerUrl");
			if (!_HttpServerUrl.ToLower().StartsWith("http"))
			{
				CLibWin.WriteToLog("[配置]HttpServerUrl:" + _HttpServerUrl.ToString() + ",错误,监听地址填写不合理");
				return "[配置]HttpServerUrl:" + _HttpServerUrl.ToString() + ",错误,监听地址填写不合理";
			}
			CLibWin.WriteToLog("[配置]HttpServerUrl:" + _HttpServerUrl.ToString());
			_InterfaceCode = CLibWin.ReadIniFile("MAIN", "InterfaceCode").Trim();
			if (_InterfaceCode != "1" && _InterfaceCode != "2" && _InterfaceCode != "3")
			{
				CLibWin.WriteToLog("[配置]InterfaceCode:" + _InterfaceCode.ToString() + ",错误,通讯接口编码填写错误");
				return "[配置]InterfaceCode:" + _InterfaceCode.ToString() + ",错误,通讯接口编码填写错误";
			}
			CLibWin.WriteToLog("[配置]InterfaceCode:" + _InterfaceCode.ToString());
			_TransMode = CLibWin.ReadIniFile("MAIN", "TransMode");
			if (_TransMode != "1" && _TransMode != "2")
			{
				CLibWin.WriteToLog("[配置]TransMode:" + _TransMode.ToString() + ",错误,传输方式填写错误");
				return "[配置]TransMode:" + _TransMode.ToString() + ",错误,传输方式填写错误";
			}
			CLibWin.WriteToLog("[配置]TransMode:" + _TransMode.ToString());
			return "";
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
			return "【错误描述】获取本地配置发生异常:" + ex.Message;
		}
	}

	private string Answer(string replycode, string replymsg)
	{
		JObject jo = new JObject();
		jo.Add("replycode", replycode);
		jo.Add("replymsg", replymsg);
		jo.Add("dtkfhz", "");
		jo.Add("fhm", "");
		jo.Add("yhhh", "");
		jo.Add("kh", "");
		jo.Add("pzh", "");
		jo.Add("sfje", "");
		jo.Add("cwsm", "");
		jo.Add("shh", "");
		jo.Add("zdh", "");
		jo.Add("pch", "");
		jo.Add("jyrq", "");
		jo.Add("jysj", "");
		jo.Add("jyckh", "");
		jo.Add("sqh", "");
		jo.Add("qsrq", "");
		jo.Add("lrcjy", "");
		jo.Add("yhje", "");
		jo.Add("klx", "");
		jo.Add("dsfyhsm", "");
		jo.Add("dsfpt", "");
		jo.Add("ysddh", "");
		jo.Add("erpddh", "");
		jo.Add("zffs", "");
		jo.Add("cxjgzt", "");
		jo.Add("cxjgms", "");
		return jo.ToString();
	}

	private string Answer(string replycode, string replymsg, string dtkfhz, string fhm, string yhhh, string kh, string pzh, string sfje, string cwsm, string shh, string zdh, string pch, string jyrq, string jysj, string jyckh, string sqh, string qsrq, string lrcjy, string yhje, string klx, string dsfyhsm, string dsfpt, string ysddh, string erpddh, string zffs, string cxjgzt, string cxjgms)
	{
		JObject jo = new JObject();
		jo.Add("replycode", replycode);
		jo.Add("replymsg", replymsg);
		jo.Add("dtkfhz", dtkfhz);
		jo.Add("fhm", fhm);
		jo.Add("yhhh", yhhh);
		jo.Add("kh", kh);
		jo.Add("pzh", pzh);
		jo.Add("sfje", sfje);
		jo.Add("cwsm", cwsm);
		jo.Add("shh", shh);
		jo.Add("zdh", zdh);
		jo.Add("pch", pch);
		jo.Add("jyrq", jyrq);
		jo.Add("jysj", jysj);
		jo.Add("jyckh", jyckh);
		jo.Add("sqh", sqh);
		jo.Add("qsrq", qsrq);
		jo.Add("lrcjy", lrcjy);
		jo.Add("yhje", yhje);
		jo.Add("klx", klx);
		jo.Add("dsfyhsm", dsfyhsm);
		jo.Add("dsfpt", dsfpt);
		jo.Add("ysddh", ysddh);
		jo.Add("erpddh", erpddh);
		jo.Add("zffs", zffs);
		jo.Add("cxjgzt", cxjgzt);
		jo.Add("cxjgms", cxjgms);
		return jo.ToString();
	}

	private bool KeyLenVerification(HttpListenerContext ctx, StreamWriter writer, Stream stream, StreamReader reader, string key, string keyvalue, int maxlen)
	{
		bool ret = false;
		try
		{
			if (keyvalue.Length > maxlen)
			{
				ret = false;
				CLibWin.WriteToLog("【内部错误提示】:" + key + "长度超出,最大长度[" + maxlen + "]");
				writer.WriteLine(Answer("-9001", "【内部错误提示】:" + key + "长度超出,最大长度[" + maxlen + "]"));
				Disconnecthttp(ctx, writer, stream, reader);
			}
			else
			{
				ret = true;
			}
			return ret;
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
			return ret;
		}
	}

	private void Disconnecthttp(HttpListenerContext ctx, StreamWriter writer, Stream stream, StreamReader reader)
	{
		try
		{
			writer.Close();
			ctx.Response.Close();
			if (_TransMode == "2")
			{
				if (stream != null)
				{
					stream.Close();
					stream.Dispose();
				}
				if (reader != null)
				{
					reader.Close();
					reader.Dispose();
				}
			}
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
		}
	}

	private string Posinfdll(string indata, out int iret, out string outdata)
	{
		outdata = "";
		iret = -1;
		try
		{
			char[] Response_Out = new char[2048];
			iret = bankall(indata.ToCharArray(), Response_Out);
			outdata = CharArrayToHexString(Response_Out);
			return "";
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
			return "【内部错误提示】调用银联动态库业务接口发生异常:" + ex.Message;
		}
	}

	[DllImport("posinf.dll", CharSet = CharSet.Ansi, SetLastError = true)]
	public static extern int bankall(char[] str1, [Out] char[] str2);

	private int LenAdd(string str)
	{
		int lenadd = 0;
		string lastc = "-1";
		for (int i = 0; i < str.Length; i++)
		{
			char c = str[i];
			int ilen = Encoding.Default.GetByteCount(c.ToString());
			if (ilen == 2)
			{
				lenadd++;
			}
			if ((c + lastc).Trim() == "")
			{
				break;
			}
			lastc = c.ToString();
		}
		return lenadd;
	}

	private string ExistsSubstring(string str, int index, int len)
	{
		try
		{
			if (str.Length >= index + len)
			{
				return str.Substring(index, len);
			}
			return "";
		}
		catch (Exception)
		{
			return "";
		}
	}

	public static string CharArrayToHexString(char[] data)
	{
		StringBuilder sb = new StringBuilder(data.Length);
		foreach (char b in data)
		{
			string s1 = b.ToString();
			if (s1 == "\0")
			{
				s1 = " ";
			}
			sb.Append(s1);
		}
		return sb.ToString().ToUpper();
	}

	private string OpenHttpListen()
	{
		try
		{
			listerner.AuthenticationSchemes = AuthenticationSchemes.Anonymous;
			listerner.Prefixes.Add(_HttpServerUrl);
			listerner.Start();
			thd_HttpListen = new Thread(thdHttpListen);
			thd_HttpListen.Start();
			thd_HttpListen.IsBackground = true;
			return "";
		}
		catch (Exception ex)
		{
			CLibWin.WriteToLog("【" + base.Name + ".cs " + ex.StackTrace.Substring(ex.StackTrace.IndexOf("行号")) + "】【错误描述】:" + ex.Message);
			return "监听开启失败," + ex;
		}
	}

	private void thdHttpListen()
	{
		HttpListenerContext ctx = null;
		StreamWriter writer = null;
		Stream stream = null;
		StreamReader reader = null;
		Encoding ecode = Encoding.GetEncoding("GBK");
		if (_InterfaceCode == "1")
		{
			ecode = Encoding.GetEncoding("GB2312");
		}
		else if (_InterfaceCode == "2")
		{
			ecode = Encoding.GetEncoding("GBK");
		}
		else if (_InterfaceCode == "3")
		{
			ecode = Encoding.UTF8;
		}
		else if (_InterfaceCode == "4")
		{
			ecode = Encoding.Default;
		}
		while (true)
		{
			bool flag = true;
			ctx = listerner.GetContext();
			ctx.Response.AddHeader("Access-Control-Allow-Origin", "*");
			ctx.Response.StatusCode = 200;
			writer = new StreamWriter(ctx.Response.OutputStream, ecode);
			try
			{
				string url = Path.GetFileName(ctx.Request.RawUrl);
				CLibWin.WriteToLog("[收到请求URL]" + ctx.Request.Url.ToString());
				string MSG = "";
				string yylx = "";
				string posjh = "";
				string posygh = "";
				string jylx = "";
				string je = "";
				string yjyrq = "";
				string yjyckh = "";
				string ypzh = "";
				string lrcjy = "";
				string cm = "";
				string ysddh = "";
				string erpddh = "";
				string body = "";
				if (_TransMode == "1")
				{
					MSG = HttpUtility.ParseQueryString(url).Get("MSG");
					yylx = HttpUtility.ParseQueryString(url).Get("yylx");
					posjh = HttpUtility.ParseQueryString(url).Get("posjh");
					posygh = HttpUtility.ParseQueryString(url).Get("posygh");
					jylx = HttpUtility.ParseQueryString(url).Get("jylx");
					je = HttpUtility.ParseQueryString(url).Get("je");
					yjyrq = HttpUtility.ParseQueryString(url).Get("yjyrq");
					yjyckh = HttpUtility.ParseQueryString(url).Get("yjyckh");
					ypzh = HttpUtility.ParseQueryString(url).Get("ypzh");
					lrcjy = HttpUtility.ParseQueryString(url).Get("lrcjy");
					cm = HttpUtility.ParseQueryString(url).Get("cm");
					ysddh = HttpUtility.ParseQueryString(url).Get("ysddh");
					erpddh = HttpUtility.ParseQueryString(url).Get("erpddh");
					goto IL_0575;
				}
				if (!(_TransMode == "2"))
				{
					goto IL_0575;
				}
				stream = ctx.Request.InputStream;
				reader = new StreamReader(stream, ecode);
				body = reader.ReadToEnd();
				if (!JsonSplit.IsJson(body))
				{
					CLibWin.WriteToLog("【" + base.Name + ".cs 行号 " + Fun.GetCodeNum() + "】【内部错误提示】:不是有效的json格式:" + body);
					writer.WriteLine(Answer("-9003", "【内部错误提示】不是有效的json格式:" + body));
					Disconnecthttp(ctx, writer, stream, reader);
					continue;
				}
				JObject outjson = JObject.Parse(body);
				MSG = ((outjson["MSG"] != null) ? outjson["MSG"].ToString() : "");
				yylx = ((outjson["yylx"] != null) ? outjson["yylx"].ToString() : "");
				posjh = ((outjson["posjh"] != null) ? outjson["posjh"].ToString() : "");
				posygh = ((outjson["posygh"] != null) ? outjson["posygh"].ToString() : "");
				jylx = ((outjson["jylx"] != null) ? outjson["jylx"].ToString() : "");
				je = ((outjson["je"] != null) ? outjson["je"].ToString() : "");
				yjyrq = ((outjson["yjyrq"] != null) ? outjson["yjyrq"].ToString() : "");
				yjyckh = ((outjson["yjyckh"] != null) ? outjson["yjyckh"].ToString() : "");
				ypzh = ((outjson["ypzh"] != null) ? outjson["ypzh"].ToString() : "");
				lrcjy = ((outjson["lrcjy"] != null) ? outjson["lrcjy"].ToString() : "");
				cm = ((outjson["cm"] != null) ? outjson["cm"].ToString() : "");
				ysddh = ((outjson["ysddh"] != null) ? outjson["ysddh"].ToString() : "");
				erpddh = ((outjson["erpddh"] != null) ? outjson["erpddh"].ToString() : "");
				goto IL_0575;
				IL_0575:
				if (MSG == "EXEC")
				{
					CLibWin.WriteToLog("准备调用银联接口...");
					CLibWin.WriteToLog("---入参---");
					CLibWin.WriteToLog("yylx=[" + yylx + "]");
					CLibWin.WriteToLog("posjh=[" + posjh + "]");
					CLibWin.WriteToLog("posygh=[" + posygh + "]");
					CLibWin.WriteToLog("jylx=[" + jylx + "]");
					CLibWin.WriteToLog("je=[" + je + "]");
					CLibWin.WriteToLog("yjyrq=[" + yjyrq + "]");
					CLibWin.WriteToLog("yjyckh=[" + yjyckh + "]");
					CLibWin.WriteToLog("ypzh=[" + ypzh + "]");
					CLibWin.WriteToLog("lrcjy=[" + lrcjy + "]");
					CLibWin.WriteToLog("cm=[" + cm + "]");
					CLibWin.WriteToLog("ysddh=[" + ysddh + "]");
					CLibWin.WriteToLog("erpddh=[" + erpddh + "]");
					if (KeyLenVerification(ctx, writer, stream, reader, "yylx", yylx, 2) && KeyLenVerification(ctx, writer, stream, reader, "posjh", posjh, 8) && KeyLenVerification(ctx, writer, stream, reader, "posygh", posygh, 8) && KeyLenVerification(ctx, writer, stream, reader, "jylx", jylx, 2) && KeyLenVerification(ctx, writer, stream, reader, "je", je, 12) && KeyLenVerification(ctx, writer, stream, reader, "yjyrq", yjyrq, 8) && KeyLenVerification(ctx, writer, stream, reader, "yjyckh", yjyckh, 12) && KeyLenVerification(ctx, writer, stream, reader, "ypzh", ypzh, 6) && KeyLenVerification(ctx, writer, stream, reader, "lrcjy", lrcjy, 3) && KeyLenVerification(ctx, writer, stream, reader, "cm", cm, 50) && KeyLenVerification(ctx, writer, stream, reader, "ysddh", ysddh, 50) && KeyLenVerification(ctx, writer, stream, reader, "erpddh", erpddh, 50))
					{
						string instr = "";
						instr += yylx.PadRight(2, ' ');
						instr += posjh.PadRight(8, ' ');
						instr += posygh.PadRight(8, ' ');
						instr += jylx.PadRight(2, ' ');
						instr += je.PadRight(12, ' ');
						instr += yjyrq.PadRight(8, ' ');
						instr += yjyckh.PadRight(12, ' ');
						instr += ypzh.PadRight(6, ' ');
						instr += lrcjy.PadRight(3, ' ');
						instr += cm.PadRight(50, ' ');
						instr += ysddh.PadRight(50, ' ');
						instr += erpddh.PadRight(50, ' ');
						CLibWin.WriteToLog("入参完整串:[" + instr + "]");
						int iret = -1;
						string outstr = "";
						string msg = Posinfdll(instr, out iret, out outstr);
						if (!(msg != ""))
						{
							CLibWin.WriteToLog("出参完整串:[" + outstr + "]");
							string dtkfhz = iret.ToString();
							string fhm = ExistsSubstring(outstr, 0, 2).Trim();
							string yhhh = ExistsSubstring(outstr, 2, 4).Trim();
							string kh = ExistsSubstring(outstr, 6, 20).Trim();
							string pzh = ExistsSubstring(outstr, 26, 6).Trim();
							string sfje = ExistsSubstring(outstr, 32, 12).Trim();
							string cwsmlen = ExistsSubstring(outstr, 44, 40);
							int lenadd1 = LenAdd(cwsmlen);
							string cwsm = ExistsSubstring(outstr, 44, 40 - lenadd1).Trim();
							string shh = ExistsSubstring(outstr, 84 - lenadd1, 15).Trim();
							string zdh = ExistsSubstring(outstr, 99 - lenadd1, 8).Trim();
							string pch = ExistsSubstring(outstr, 107 - lenadd1, 6).Trim();
							string jyrq = ExistsSubstring(outstr, 113 - lenadd1, 4).Trim();
							string jysj = ExistsSubstring(outstr, 117 - lenadd1, 6).Trim();
							string jyckh = ExistsSubstring(outstr, 123 - lenadd1, 12).Trim();
							string sqh = ExistsSubstring(outstr, 135 - lenadd1, 6).Trim();
							string qsrq = ExistsSubstring(outstr, 141 - lenadd1, 4).Trim();
							string lcrjy2 = ExistsSubstring(outstr, 145 - lenadd1, 3).Trim();
							string yhje = ExistsSubstring(outstr, 148 - lenadd1, 12).Trim();
							string klx = ExistsSubstring(outstr, 160 - lenadd1, 2).Trim();
							string dsfyhlen = ExistsSubstring(outstr, 162 - lenadd1, 200);
							int lenadd2 = LenAdd(dsfyhlen);
							string dsfyhsm = ExistsSubstring(outstr, 162 - lenadd1, 200 - lenadd2).Trim();
							string dsfptlen = ExistsSubstring(outstr, 362 - lenadd1 - lenadd2, 50);
							int lenadd3 = LenAdd(dsfptlen);
							string dsfpt = ExistsSubstring(outstr, 362 - lenadd1 - lenadd2, 50 - lenadd3).Trim();
							string ysddh2 = ExistsSubstring(outstr, 412 - lenadd1 - lenadd2 - lenadd3, 50).Trim();
							string erpddh2 = ExistsSubstring(outstr, 462 - lenadd1 - lenadd2 - lenadd3, 50).Trim();
							string zffs = ExistsSubstring(outstr, 512 - lenadd1 - lenadd2 - lenadd3, 1).Trim();
							string cxjgzt = ExistsSubstring(outstr, 513 - lenadd1 - lenadd2 - lenadd3, 1).Trim();
							string cxjgms = ExistsSubstring(outstr, 514 - lenadd1 - lenadd2 - lenadd3, 50).Trim();
							CLibWin.WriteToLog("---出参---");
							CLibWin.WriteToLog("dtkfhz=[" + dtkfhz + "]");
							CLibWin.WriteToLog("fhm=[" + fhm + "]");
							CLibWin.WriteToLog("yhhh=[" + yhhh + "]");
							CLibWin.WriteToLog("kh=[" + kh + "]");
							CLibWin.WriteToLog("pzh=[" + pzh + "]");
							CLibWin.WriteToLog("sfje=[" + sfje + "]");
							CLibWin.WriteToLog("cwsm=[" + cwsm + "]");
							CLibWin.WriteToLog("shh=[" + shh + "]");
							CLibWin.WriteToLog("zdh=[" + zdh + "]");
							CLibWin.WriteToLog("pch=[" + pch + "]");
							CLibWin.WriteToLog("jyrq=[" + jyrq + "]");
							CLibWin.WriteToLog("jysj=[" + jysj + "]");
							CLibWin.WriteToLog("jyckh=[" + jyckh + "]");
							CLibWin.WriteToLog("sqh=[" + sqh + "]");
							CLibWin.WriteToLog("qsrq=[" + qsrq + "]");
							CLibWin.WriteToLog("lrcjy=[" + lcrjy2 + "]");
							CLibWin.WriteToLog("yhje=[" + yhje + "]");
							CLibWin.WriteToLog("klx=[" + klx + "]");
							CLibWin.WriteToLog("dsfyhsm=[" + dsfyhsm + "]");
							CLibWin.WriteToLog("dsfpt=[" + dsfpt + "]");
							CLibWin.WriteToLog("ysddh=[" + ysddh2 + "]");
							CLibWin.WriteToLog("erpddh=[" + erpddh2 + "]");
							CLibWin.WriteToLog("zffs=[" + zffs + "]");
							CLibWin.WriteToLog("cxjgzt=[" + cxjgzt + "]");
							CLibWin.WriteToLog("cxjgms=[" + cxjgms + "]");
							writer.WriteLine(Answer("1", "", dtkfhz, fhm, yhhh, kh, pzh, sfje, cwsm, shh, zdh, pch, jyrq, jysj, jyckh, sqh, qsrq, lcrjy2, yhje, klx, dsfyhsm, dsfpt, ysddh2, erpddh2, zffs, cxjgzt, cxjgms));
							Disconnecthttp(ctx, writer, stream, reader);
							CLibWin.WriteToLog("银联接口调用完毕");
							goto IL_1108;
						}
						CLibWin.WriteToLog("【内部错误提示】:调用Posinfdll()发生异常," + msg);
						writer.WriteLine(Answer("-9002", "【内部错误提示】:调用Posinfdll()发生异常," + msg));
						Disconnecthttp(ctx, writer, stream, reader);
					}
					continue;
				}
				if (MSG == "TEST")
				{
					CLibWin.WriteToLog("准备调用测试接口...");
					JObject answerjson = new JObject();
					if (_TransMode == "1")
					{
						foreach (string key in HttpUtility.ParseQueryString(url).Keys)
						{
							answerjson.Add(key, HttpUtility.ParseQueryString(url).Get(key));
						}
					}
					else if (_TransMode == "2")
					{
						answerjson = JObject.Parse(body);
					}
					CLibWin.WriteToLog("入参/出参:" + answerjson.ToString());
					writer.WriteLine(answerjson);
					Disconnecthttp(ctx, writer, stream, reader);
					CLibWin.WriteToLog("测试接口调用完毕");
				}
				else
				{
					CLibWin.WriteToLog("收到未知请求URL:" + ctx.Request.Url.ToString());
					Disconnecthttp(ctx, writer, stream, reader);
				}
				goto IL_1108;
			}
			catch (Exception exx)
			{
				CLibWin.WriteToLog("【" + base.Name + ".cs " + exx.StackTrace.Substring(exx.StackTrace.IndexOf("行号")) + "】【内部错误提示】:" + exx.Message);
				if (writer != null)
				{
					try
					{
						writer.WriteLine(Answer("-9004", "【内部错误提示】监听内发生异常," + exx.Message));
						writer.Close();
					}
					catch
					{
					}
				}
				if (ctx != null)
				{
					try
					{
						ctx.Response.Close();
					}
					catch
					{
					}
				}
				goto IL_1108;
			}
			IL_1108:
			Thread.Sleep(10);
		}
	}

	private void timerClearMom_Tick(object sender, EventArgs e)
	{
		try
		{
			SetProcessWorkingSetSize(Process.GetCurrentProcess().Handle, -1, -1);
		}
		catch
		{
		}
	}

	[DllImport("kernel32.dll")]
	public static extern int SetProcessWorkingSetSize(IntPtr hProcess, int dwMinimumWorkingSetSize, int dwMaximumWorkingSetSize);

	protected override void Dispose(bool disposing)
	{
		if (disposing && components != null)
		{
			components.Dispose();
		}
		base.Dispose(disposing);
	}

	private void InitializeComponent()
	{
		this.components = new System.ComponentModel.Container();
		System.ComponentModel.ComponentResourceManager resources = new System.ComponentModel.ComponentResourceManager(typeof(UnionPayPlugin.MainForm));
		this.label4 = new System.Windows.Forms.Label();
		this.label2 = new System.Windows.Forms.Label();
		this.label3 = new System.Windows.Forms.Label();
		this.pbTop = new System.Windows.Forms.PictureBox();
		this.lblProduct = new System.Windows.Forms.Label();
		this.lblVersion = new System.Windows.Forms.Label();
		this.nIcon = new System.Windows.Forms.NotifyIcon(this.components);
		this.cmsRightMenu = new System.Windows.Forms.ContextMenuStrip(this.components);
		this.tsmiOpenSys = new System.Windows.Forms.ToolStripMenuItem();
		this.tsmiExitSys = new System.Windows.Forms.ToolStripMenuItem();
		this.timerClearMom = new System.Windows.Forms.Timer(this.components);
		((System.ComponentModel.ISupportInitialize)this.pbTop).BeginInit();
		this.cmsRightMenu.SuspendLayout();
		base.SuspendLayout();
		this.label4.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.label4.AutoSize = true;
		this.label4.Location = new System.Drawing.Point(44, 112);
		this.label4.Name = "label4";
		this.label4.Size = new System.Drawing.Size(41, 12);
		this.label4.TabIndex = 23;
		this.label4.Text = "版本：";
		this.label2.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.label2.AutoSize = true;
		this.label2.Location = new System.Drawing.Point(44, 93);
		this.label2.Name = "label2";
		this.label2.Size = new System.Drawing.Size(41, 12);
		this.label2.TabIndex = 22;
		this.label2.Text = "产品：";
		this.label3.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.label3.AutoSize = true;
		this.label3.BackColor = System.Drawing.Color.White;
		this.label3.Font = new System.Drawing.Font("宋体", 18f, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Point, 134);
		this.label3.Location = new System.Drawing.Point(110, 37);
		this.label3.Name = "label3";
		this.label3.Size = new System.Drawing.Size(60, 24);
		this.label3.TabIndex = 21;
		this.label3.Text = "银联";
		this.pbTop.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.pbTop.Image = (System.Drawing.Image)resources.GetObject("pbTop.Image");
		this.pbTop.Location = new System.Drawing.Point(64, 29);
		this.pbTop.Name = "pbTop";
		this.pbTop.Size = new System.Drawing.Size(40, 40);
		this.pbTop.SizeMode = System.Windows.Forms.PictureBoxSizeMode.StretchImage;
		this.pbTop.TabIndex = 20;
		this.pbTop.TabStop = false;
		this.lblProduct.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.lblProduct.AutoSize = true;
		this.lblProduct.Location = new System.Drawing.Point(91, 93);
		this.lblProduct.Name = "lblProduct";
		this.lblProduct.Size = new System.Drawing.Size(53, 12);
		this.lblProduct.TabIndex = 25;
		this.lblProduct.Text = "接口插件";
		this.lblVersion.Anchor = System.Windows.Forms.AnchorStyles.Top;
		this.lblVersion.AutoSize = true;
		this.lblVersion.Location = new System.Drawing.Point(91, 112);
		this.lblVersion.Name = "lblVersion";
		this.lblVersion.Size = new System.Drawing.Size(23, 12);
		this.lblVersion.TabIndex = 26;
		this.lblVersion.Text = "1.0";
		this.nIcon.BalloonTipText = "银联-接口插件";
		this.nIcon.BalloonTipTitle = "银联-接口插件";
		this.nIcon.ContextMenuStrip = this.cmsRightMenu;
		this.nIcon.Icon = (System.Drawing.Icon)resources.GetObject("nIcon.Icon");
		this.nIcon.Text = "银联-接口插件";
		this.nIcon.Visible = true;
		this.nIcon.MouseDoubleClick += new System.Windows.Forms.MouseEventHandler(nIcon_MouseDoubleClick);
		this.cmsRightMenu.Items.AddRange(new System.Windows.Forms.ToolStripItem[2] { this.tsmiOpenSys, this.tsmiExitSys });
		this.cmsRightMenu.Name = "contextMenuStrip1";
		this.cmsRightMenu.Size = new System.Drawing.Size(125, 48);
		this.tsmiOpenSys.Name = "tsmiOpenSys";
		this.tsmiOpenSys.Size = new System.Drawing.Size(124, 22);
		this.tsmiOpenSys.Text = "打开系统";
		this.tsmiOpenSys.Click += new System.EventHandler(tsmiOpenSys_Click);
		this.tsmiExitSys.Name = "tsmiExitSys";
		this.tsmiExitSys.Size = new System.Drawing.Size(124, 22);
		this.tsmiExitSys.Text = "退出系统";
		this.tsmiExitSys.Click += new System.EventHandler(tsmiExitSys_Click);
		this.timerClearMom.Enabled = true;
		this.timerClearMom.Interval = 600000;
		this.timerClearMom.Tick += new System.EventHandler(timerClearMom_Tick);
		base.AutoScaleDimensions = new System.Drawing.SizeF(6f, 12f);
		base.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
		this.BackColor = System.Drawing.Color.White;
		base.ClientSize = new System.Drawing.Size(324, 162);
		base.Controls.Add(this.lblVersion);
		base.Controls.Add(this.lblProduct);
		base.Controls.Add(this.label4);
		base.Controls.Add(this.label2);
		base.Controls.Add(this.label3);
		base.Controls.Add(this.pbTop);
		base.FormBorderStyle = System.Windows.Forms.FormBorderStyle.FixedSingle;
		base.Icon = (System.Drawing.Icon)resources.GetObject("$this.Icon");
		base.MaximizeBox = false;
		this.MinimumSize = new System.Drawing.Size(330, 190);
		base.Name = "MainForm";
		base.StartPosition = System.Windows.Forms.FormStartPosition.CenterScreen;
		this.Text = "银联-接口插件";
		base.FormClosing += new System.Windows.Forms.FormClosingEventHandler(MainForm_FormClosing);
		base.SizeChanged += new System.EventHandler(MainForm_SizeChanged);
		((System.ComponentModel.ISupportInitialize)this.pbTop).EndInit();
		this.cmsRightMenu.ResumeLayout(false);
		base.ResumeLayout(false);
		base.PerformLayout();
	}
}

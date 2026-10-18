using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace UnionPayPlugin;

public class CLibWin
{
	public static string strIniFile = ".\\config.ini";

	public static object lock_obj = new object();

	public static void SetIniFilePath(string strFile)
	{
		strIniFile = strFile;
	}

	public static string ReadIniFile(string pszApp, string pszKey)
	{
		StringBuilder retVal = new StringBuilder(256);
		int ret = GetPrivateProfileString(pszApp, pszKey, "", retVal, 255, strIniFile);
		return retVal.ToString().Trim();
	}

	[DllImport("kernel32")]
	public static extern int GetPrivateProfileString(string section, string key, string def, StringBuilder retVal, int size, string filePath);

	public static void WriteToLog(string strMessage)
	{
		string str = AppDomain.CurrentDomain.BaseDirectory;
		string filename = str + "log\\" + DateTime.Now.ToString("yyyyMMdd") + ".log";
		StreamWriter sw = null;
		lock (lock_obj)
		{
			try
			{
				if (!File.Exists(filename))
				{
					if (!Directory.Exists(str + "log"))
					{
						Directory.CreateDirectory(str + "\\log");
					}
					sw = File.CreateText(filename);
				}
				else
				{
					sw = File.AppendText(filename);
				}
				sw.WriteLine(DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss,fff") + " " + strMessage);
				sw.Flush();
			}
			catch (Exception)
			{
				throw;
			}
			finally
			{
				sw?.Close();
			}
		}
	}
}

using System;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Text.RegularExpressions;

namespace UnionPayPlugin;

public class Fun
{
	public static bool CollapseRunTest()
	{
		try
		{
			int iii = Convert.ToInt32("abc");
			return true;
		}
		catch (Exception ex)
		{
			if (ex.StackTrace.Contains("行号"))
			{
				return true;
			}
			return false;
		}
	}

	public static string GetRemovePrefixString(string val, string str)
	{
		string strRegex = "^(" + str + ")";
		return Regex.Replace(val, strRegex, "");
	}

	public static string GetRemoveSuffixString(string val, string str)
	{
		string strRegex = "(" + str + ")$";
		return Regex.Replace(val, strRegex, "");
	}

	public static byte[] GetBeforeByte(byte[] byt, int count)
	{
		byte[] result = new byte[count];
		for (int i = 0; i < count; i++)
		{
			result[i] = byt[i];
		}
		return result;
	}

	public static bool CheckIpReasonable(string ip)
	{
		try
		{
			if (IPAddress.TryParse(ip, out var _))
			{
				return true;
			}
			return false;
		}
		catch
		{
			return false;
		}
	}

	public static bool CheckPort(int port)
	{
		bool b = false;
		try
		{
			if (IsNumeric(port.ToString()))
			{
				b = ((65536 >= Convert.ToInt32(port) && Convert.ToInt32(port) > 0) ? true : false);
			}
			return b;
		}
		catch
		{
			return false;
		}
	}

	public static bool IsNumeric(string str)
	{
		Regex reg1 = new Regex("^[0-9]\\d*$");
		return reg1.IsMatch(str);
	}

	public static bool PortInUse(int port)
	{
		bool inUse = false;
		IPGlobalProperties ipProperties = IPGlobalProperties.GetIPGlobalProperties();
		IPEndPoint[] ipEndPoints = ipProperties.GetActiveTcpListeners();
		IPEndPoint[] array = ipEndPoints;
		foreach (IPEndPoint endPoint in array)
		{
			if (endPoint.Port == port)
			{
				inUse = true;
				break;
			}
		}
		return inUse;
	}

	public static string GetCodeNum()
	{
		StackTrace st = new StackTrace(1, fNeedFileInfo: true);
		return st.GetFrame(0).GetFileLineNumber().ToString();
	}
}

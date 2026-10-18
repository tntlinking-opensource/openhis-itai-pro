namespace UnionPayPlugin;

public class JsonSplit
{
	private class CharState
	{
		internal bool jsonStart = false;

		internal bool setDicValue = false;

		internal bool escapeChar = false;

		internal bool arrayStart = false;

		internal bool childrenStart = false;

		internal int state = 0;

		internal int keyStart = 0;

		internal int valueStart = 0;

		internal bool isError = false;

		internal void CheckIsError(char c)
		{
			if (keyStart > 1 || valueStart > 1)
			{
				return;
			}
			switch (c)
			{
			case '{':
				isError = jsonStart && state == 0;
				break;
			case '}':
				isError = !jsonStart || (keyStart != 0 && state == 0);
				break;
			case '[':
				isError = arrayStart && state == 0;
				break;
			case ']':
				isError = !arrayStart || jsonStart;
				break;
			case '"':
			case '\'':
				isError = !jsonStart && !arrayStart;
				if (!isError)
				{
					isError = (state == 0 && keyStart == -1) || (state == 1 && valueStart == -1);
				}
				if (!isError && arrayStart && !jsonStart && c == '\'')
				{
					isError = true;
				}
				break;
			case ':':
				isError = !jsonStart || state == 1;
				break;
			case ',':
				isError = !jsonStart && !arrayStart;
				if (!isError)
				{
					if (jsonStart)
					{
						isError = state == 0 || (state == 1 && valueStart > 1);
					}
					else if (arrayStart)
					{
						isError = keyStart == 0 && !setDicValue;
					}
				}
				break;
			case '\0':
			case '\t':
			case '\n':
			case '\r':
			case ' ':
				break;
			default:
				isError = (!jsonStart && !arrayStart) || (state == 0 && keyStart == -1) || (valueStart == -1 && state == 1);
				break;
			}
		}
	}

	private static bool IsJsonStart(ref string json)
	{
		if (!string.IsNullOrEmpty(json))
		{
			json = json.Trim('\r', '\n', ' ');
			if (json.Length > 1)
			{
				char s = json[0];
				char e = json[json.Length - 1];
				return (s == '{' && e == '}') || (s == '[' && e == ']');
			}
		}
		return false;
	}

	public static bool IsJson(string json)
	{
		int errIndex;
		return IsJson(json, out errIndex);
	}

	internal static bool IsJson(string json, out int errIndex)
	{
		errIndex = 0;
		if (IsJsonStart(ref json))
		{
			CharState cs = new CharState();
			for (int i = 0; i < json.Length; i++)
			{
				char c = json[i];
				if (SetCharState(c, ref cs) && cs.childrenStart)
				{
					string item = json.Substring(i);
					int err;
					int length = GetValueLength(item, breakOnErr: true, out err);
					cs.childrenStart = false;
					if (err > 0)
					{
						errIndex = i + err;
						return false;
					}
					i = i + length - 1;
				}
				if (cs.isError)
				{
					errIndex = i;
					return false;
				}
			}
			return !cs.arrayStart && !cs.jsonStart;
		}
		return false;
	}

	private static int GetValueLength(string json, bool breakOnErr, out int errIndex)
	{
		errIndex = 0;
		int len = 0;
		if (!string.IsNullOrEmpty(json))
		{
			CharState cs = new CharState();
			for (int i = 0; i < json.Length; i++)
			{
				char c = json[i];
				if (!SetCharState(c, ref cs))
				{
					if (!cs.jsonStart && !cs.arrayStart)
					{
						break;
					}
				}
				else if (cs.childrenStart)
				{
					int length = GetValueLength(json.Substring(i), breakOnErr, out errIndex);
					cs.childrenStart = false;
					cs.valueStart = 0;
					i = i + length - 1;
				}
				if (breakOnErr && cs.isError)
				{
					errIndex = i;
					return i;
				}
				if (!cs.jsonStart && !cs.arrayStart)
				{
					len = i + 1;
					break;
				}
			}
		}
		return len;
	}

	private static bool SetCharState(char c, ref CharState cs)
	{
		cs.CheckIsError(c);
		switch (c)
		{
		case '{':
			if (cs.keyStart <= 0 && cs.valueStart <= 0)
			{
				cs.keyStart = 0;
				cs.valueStart = 0;
				if (cs.jsonStart && cs.state == 1)
				{
					cs.childrenStart = true;
				}
				else
				{
					cs.state = 0;
				}
				cs.jsonStart = true;
				return true;
			}
			break;
		case '}':
			if (cs.keyStart <= 0 && cs.valueStart < 2 && cs.jsonStart)
			{
				cs.jsonStart = false;
				cs.state = 0;
				cs.keyStart = 0;
				cs.valueStart = 0;
				cs.setDicValue = true;
				return true;
			}
			break;
		case '[':
			if (!cs.jsonStart)
			{
				cs.arrayStart = true;
				return true;
			}
			if (cs.jsonStart && cs.state == 1)
			{
				cs.childrenStart = true;
				return true;
			}
			break;
		case ']':
			if (cs.arrayStart && !cs.jsonStart && cs.keyStart <= 2 && cs.valueStart <= 0)
			{
				cs.keyStart = 0;
				cs.valueStart = 0;
				cs.arrayStart = false;
				return true;
			}
			break;
		case '"':
		case '\'':
			if (!cs.jsonStart && !cs.arrayStart)
			{
				break;
			}
			if (cs.state == 0)
			{
				if (cs.keyStart <= 0)
				{
					cs.keyStart = ((c == '"') ? 3 : 2);
					return true;
				}
				if ((cs.keyStart == 2 && c == '\'') || (cs.keyStart == 3 && c == '"'))
				{
					if (!cs.escapeChar)
					{
						cs.keyStart = -1;
						return true;
					}
					cs.escapeChar = false;
				}
			}
			else
			{
				if (cs.state != 1 || !cs.jsonStart)
				{
					break;
				}
				if (cs.valueStart <= 0)
				{
					cs.valueStart = ((c == '"') ? 3 : 2);
					return true;
				}
				if ((cs.valueStart == 2 && c == '\'') || (cs.valueStart == 3 && c == '"'))
				{
					if (!cs.escapeChar)
					{
						cs.valueStart = -1;
						return true;
					}
					cs.escapeChar = false;
				}
			}
			break;
		case ':':
			if (cs.jsonStart && cs.keyStart < 2 && cs.valueStart < 2 && cs.state == 0)
			{
				if (cs.keyStart == 1)
				{
					cs.keyStart = -1;
				}
				cs.state = 1;
				return true;
			}
			break;
		case ',':
			if (cs.jsonStart)
			{
				if (cs.keyStart < 2 && cs.valueStart < 2 && cs.state == 1)
				{
					cs.state = 0;
					cs.keyStart = 0;
					cs.valueStart = 0;
					cs.setDicValue = true;
					return true;
				}
			}
			else if (cs.arrayStart && cs.keyStart <= 2)
			{
				cs.keyStart = 0;
				return true;
			}
			break;
		case '\0':
		case '\t':
		case '\n':
		case '\r':
		case ' ':
			if (cs.keyStart <= 0 && cs.valueStart <= 0)
			{
				return true;
			}
			break;
		default:
			if (c == '\\')
			{
				if (!cs.escapeChar)
				{
					cs.escapeChar = true;
					return true;
				}
				cs.escapeChar = false;
			}
			else
			{
				cs.escapeChar = false;
			}
			if (cs.jsonStart || cs.arrayStart)
			{
				if (cs.keyStart <= 0 && cs.state == 0)
				{
					cs.keyStart = 1;
				}
				else if (cs.valueStart <= 0 && cs.state == 1 && cs.jsonStart)
				{
					cs.valueStart = 1;
				}
			}
			break;
		}
		return false;
	}
}

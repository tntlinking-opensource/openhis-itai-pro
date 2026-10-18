using System;
using System.Threading;
using System.Windows.Forms;

namespace UnionPayPlugin;

internal static class Program
{
	private static Mutex mutex;

	[STAThread]
	private static void Main()
	{
		Application.EnableVisualStyles();
		Application.SetCompatibleTextRenderingDefault(defaultValue: false);
		mutex = new Mutex(initiallyOwned: true, "UnionPayPlugin");
		if (mutex.WaitOne(0, exitContext: false))
		{
			Application.Run(new MainForm());
		}
	}
}

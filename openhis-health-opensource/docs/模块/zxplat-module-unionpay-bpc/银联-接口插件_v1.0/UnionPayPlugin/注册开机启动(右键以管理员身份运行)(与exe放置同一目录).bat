::win7以管理员身份运行,杀毒提示点允许.未成功关杀毒再运行.
reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v UnionPayPlugin /d "%cd%\UnionPayPlugin.exe" /f



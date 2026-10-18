@echo off

::设置程序或文件的完整路径（必选）

set Program=%~dp0\startup_mspringm.vbs

echo 当前批处理全路径：%Program%


::设置快捷方式名称（必选）

set LnkName=WebSocket启动

 

::设置程序的工作路径，一般为程序主目录，此项若留空，脚本将自行分析路径

set WorkDir=

 

::设置快捷方式显示的说明（可选）

set Desc=测试

 

if not defined WorkDir call:GetWorkDir "%Program%"

(echo Set WshShell=CreateObject("WScript.Shell"^)

echo strDesKtop=WshShell.SpecialFolders("StartUp"^)

echo Set oShellLink=WshShell.CreateShortcut(strDesKtop^&"\%LnkName%.lnk"^)

echo oShellLink.TargetPath="%Program%"

echo oShellLink.WorkingDirectory="%WorkDir%"

echo oShellLink.WindowStyle=1

echo oShellLink.Description="%Desc%"

echo oShellLink.Save)>makelnk.vbs

echo 自启动快捷方式创建成功！

makelnk.vbs
del /f /q makelnk.vbs
pause

exit

goto :eof

:GetWorkDir

set WorkDir=%~dp1

set WorkDir=%WorkDir:~,-1%

goto :eof
pause
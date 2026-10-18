@echo off
rem 功能：支持管理员权限注册ocx插件
rem ycy


cd /d %~dp0
::显示当前路径
echo 前路径：当%cd%

SET Sender1=%cd%\umsocx.ocx
echo 注册：%Sender1%
regsvr32 -v  "%Sender1%"
pause

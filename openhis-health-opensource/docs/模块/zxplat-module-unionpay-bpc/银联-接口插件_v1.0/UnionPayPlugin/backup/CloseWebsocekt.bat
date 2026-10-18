@echo off
tasklist|find /i "websocket.exe" && taskkill /f /im "websocket.exe"
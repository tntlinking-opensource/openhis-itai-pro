del .\bank\*.log
del .\pos\*.log
del .\tmis\*.log
del .\nopos\*.log
del .\yfcard\*.log
del .\*.log
del .\*.bmp

del .\log\*.log

XCOPY .\postrans.dll .\bank /Y
XCOPY .\postrans.dll .\pos /Y

del .\postrans.dll


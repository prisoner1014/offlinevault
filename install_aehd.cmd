@echo off
cd /d "%LOCALAPPDATA%\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver"
echo Starting AEHD driver install...
pnputil.exe /add-driver aehd.Inf /install
sc query aehd
sc start aehd
pause

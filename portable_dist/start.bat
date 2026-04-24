@echo off
setlocal
set "BASE=%~dp0"
"%BASE%app\runtime\bin\javaw.exe" -Dfile.encoding=UTF-8 -cp "%BASE%app\lab5_MOPPR.jar;%BASE%app\lib\*" org.example.Main
endlocal

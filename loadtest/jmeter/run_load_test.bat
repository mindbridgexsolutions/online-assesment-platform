@echo off
setlocal
rem ---- DEFAULTS (change here if needed) ----
set HOST=172.16.100.66
set PORT=8080
set STUDENTS=50
set RAMPUP=30
set SAVES=4
if "%JMETER_HOME%"=="" set JMETER_HOME=C:\jmeter\apache-jmeter-5.6.3
rem ------------------------------------------
set HERE=%~dp0
if not exist "%JMETER_HOME%\bin\jmeter.bat" (
  echo JMeter not found at %JMETER_HOME%
  echo Edit JMETER_HOME in this file to the folder where you unzipped JMeter.
  pause & exit /b 1
)
set /p EXAMID=Exam id of the TEST exam: 
if "%EXAMID%"=="" ( echo Exam id is required. & pause & exit /b 1 )
set /p IN=Number of students [%STUDENTS%]: 
if not "%IN%"=="" set STUDENTS=%IN%
echo.
echo Target : http://%HOST%:%PORT%   exam %EXAMID%
echo Load   : %STUDENTS% students, ramp-up %RAMPUP%s, %SAVES% autosaves each
echo Use TEST students and a TEST exam only, never during a real exam.
set /p OK=Type YES to start: 
if /i not "%OK%"=="YES" ( echo Cancelled. & pause & exit /b 0 )
for /f %%i in ('powershell -nologo -command "Get-Date -Format yyyyMMdd-HHmmss"') do set TS=%%i
set OUT=%HERE%results-%TS%
mkdir "%OUT%"
call "%JMETER_HOME%\bin\jmeter.bat" -n -t "%HERE%exam_load.jmx" -Jhost=%HOST% -Jport=%PORT% -Jstudents=%STUDENTS% -Jrampup=%RAMPUP% -Jsaves=%SAVES% -Jcsv="%HERE%students.csv" -JexamId=%EXAMID% -l "%OUT%\results.jtl" -j "%OUT%\jmeter.log" -e -o "%OUT%\report"
echo.
echo Done. Open this report in a browser:  %OUT%\report\index.html
pause

@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: Java not found at "%JAVA_HOME%\bin\java.exe"
    pause
    exit /b 1
)
echo Using Java from %JAVA_HOME%
call gradlew.bat lwjgl3:run
pause

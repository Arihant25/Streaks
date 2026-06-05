@echo off
cd /d "%~dp0"
echo.
echo === Streaks Build ===
echo.
if not "%ANDROID_HOME%"=="" goto :sdk_ok
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" ( set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk" & goto :sdk_ok )
if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" ( set "ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk" & goto :sdk_ok )
echo [ERROR] Android SDK not found. Install Android Studio: https://developer.android.com/studio
pause & exit /b 1
:sdk_ok
echo SDK: %ANDROID_HOME%
if not "%JAVA_HOME%"=="" goto :java_ok
for %%p in ( "%PROGRAMFILES%\Android\Android Studio\jbr" "%PROGRAMFILES%\Android\Android Studio\jre" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jre" ) do ( if exist "%%~p\bin\javac.exe" ( set "JAVA_HOME=%%~p" & goto :java_ok ) )
echo [ERROR] JDK not found. Install Android Studio (it includes JDK).
pause & exit /b 1
:java_ok
echo JDK: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo.
echo Building APK (first run ~5 min, subsequent ~30 sec)...
echo.
call gradlew.bat assembleDebug --no-daemon
if not exist "app\build\outputs\apk\debug\app-debug.apk" ( echo. & echo [ERROR] Build failed. See output above. & pause & exit /b 1 )
echo.
echo [OK] APK ready: %~dp0app\build\outputs\apk\debug\app-debug.apk
echo.
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not exist "%ADB%" goto :no_adb
"%ADB%" devices 2>nul | findstr /r "device$" >nul
if %ERRORLEVEL% neq 0 goto :no_adb
echo Phone connected - installing...
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
echo [OK] Installed on phone!
goto :done
:no_adb
echo No phone found via USB.
echo Copy APK to phone: app\build\outputs\apk\debug\app-debug.apk
:done
echo.
pause

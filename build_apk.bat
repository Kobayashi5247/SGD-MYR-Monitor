@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title SgdMyrMonitor - 一键安装依赖并编译 APK

rem ============================================================
rem  把本文件放在项目根目录（和 settings.gradle.kts 同级），双击运行。
rem  自动检测 / 安装：JDK 17、Gradle 8.9、Android SDK (platform 34 + build-tools 34.0.0)
rem  已有的会直接复用；缺少的下载到项目下的 .tools 文件夹（不需要管理员权限，不改系统环境）。
rem  成功后在本目录生成 SgdMyrMonitor.apk
rem ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "TOOLS=%ROOT%\.tools"
set "GRADLE_VER=8.9"
set "PLATFORM=android-34"
set "BT_VER=34.0.0"
set "JDK_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
set "GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip"
set "CMDTOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

cd /d "%ROOT%"
echo ==================================================
echo   SgdMyrMonitor 一键编译 APK
echo   项目目录: %ROOT%
echo ==================================================
echo.

if not exist "%ROOT%\settings.gradle.kts" (
    echo [错误] 没找到 settings.gradle.kts，请把本 bat 放到项目根目录再运行。
    goto :fail
)
if not exist "%TOOLS%" mkdir "%TOOLS%"

rem ---------------------------------------------------------
rem  [1/4] JDK 17
rem ---------------------------------------------------------
echo [1/4] 检测 JDK 17 ...
set "JAVA_EXE="
if not defined JAVA_HOME goto :j_path
call :check_java "!JAVA_HOME!\bin\java.exe"
if "!JAVA_OK!"=="1" set "JAVA_EXE=!JAVA_HOME!\bin\java.exe"

:j_path
if defined JAVA_EXE goto :j_done
set "PJ="
for /f "delims=" %%p in ('where java.exe 2^>nul') do if not defined PJ set "PJ=%%p"
if not defined PJ goto :j_local
call :check_java "!PJ!"
if "!JAVA_OK!"=="1" set "JAVA_EXE=!PJ!"

:j_local
if defined JAVA_EXE goto :j_done
call :check_java "%TOOLS%\jdk17\bin\java.exe"
if "!JAVA_OK!"=="1" set "JAVA_EXE=%TOOLS%\jdk17\bin\java.exe"
if defined JAVA_EXE goto :j_done

echo     未检测到可用的 JDK 17/21，开始下载 Temurin JDK 17 ^(约 180MB^) ...
call :download "%JDK_URL%" "%TOOLS%\jdk17.zip" || goto :fail
call :unzip "%TOOLS%\jdk17.zip" "%TOOLS%\_jdk_tmp" || goto :fail
if exist "%TOOLS%\jdk17" rmdir /s /q "%TOOLS%\jdk17"
for /d %%d in ("%TOOLS%\_jdk_tmp\*") do move "%%d" "%TOOLS%\jdk17" >nul
rmdir /s /q "%TOOLS%\_jdk_tmp" 2>nul
del "%TOOLS%\jdk17.zip" 2>nul
if not exist "%TOOLS%\jdk17\bin\java.exe" (
    echo [错误] JDK 解压后没找到 java.exe
    goto :fail
)
set "JAVA_EXE=%TOOLS%\jdk17\bin\java.exe"

:j_done
for %%i in ("!JAVA_EXE!") do set "JBIN=%%~dpi"
for %%i in ("!JBIN!..") do set "JAVA_HOME=%%~fi"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo     OK  JDK: %JAVA_HOME%
echo.

rem ---------------------------------------------------------
rem  [2/4] Gradle
rem ---------------------------------------------------------
echo [2/4] 检测 Gradle ^(需要 8.7 ~ 8.x^) ...
set "GRADLE_BAT="
set "PG="
for /f "delims=" %%p in ('where gradle.bat 2^>nul') do if not defined PG set "PG=%%p"
if not defined PG goto :g_local
call :check_gradle "!PG!"
if "!GRADLE_OK!"=="1" set "GRADLE_BAT=!PG!"

:g_local
if defined GRADLE_BAT goto :g_done
if exist "%TOOLS%\gradle-%GRADLE_VER%\bin\gradle.bat" set "GRADLE_BAT=%TOOLS%\gradle-%GRADLE_VER%\bin\gradle.bat"
if defined GRADLE_BAT goto :g_done

echo     未检测到合适的 Gradle，开始下载 Gradle %GRADLE_VER% ^(约 130MB^) ...
call :download "%GRADLE_URL%" "%TOOLS%\gradle.zip" || goto :fail
call :unzip "%TOOLS%\gradle.zip" "%TOOLS%\_gradle_tmp" || goto :fail
if exist "%TOOLS%\gradle-%GRADLE_VER%" rmdir /s /q "%TOOLS%\gradle-%GRADLE_VER%"
move "%TOOLS%\_gradle_tmp\gradle-%GRADLE_VER%" "%TOOLS%\gradle-%GRADLE_VER%" >nul
rmdir /s /q "%TOOLS%\_gradle_tmp" 2>nul
del "%TOOLS%\gradle.zip" 2>nul
if not exist "%TOOLS%\gradle-%GRADLE_VER%\bin\gradle.bat" (
    echo [错误] Gradle 解压后没找到 gradle.bat
    goto :fail
)
set "GRADLE_BAT=%TOOLS%\gradle-%GRADLE_VER%\bin\gradle.bat"

:g_done
echo     OK  Gradle: %GRADLE_BAT%
echo.

rem ---------------------------------------------------------
rem  [3/4] Android SDK
rem ---------------------------------------------------------
echo [3/4] 检测 Android SDK ^(%PLATFORM% + build-tools %BT_VER%^) ...
set "SDK_DIR="
call :check_sdk "%ANDROID_SDK_ROOT%"
call :check_sdk "%ANDROID_HOME%"
call :check_sdk "%LOCALAPPDATA%\Android\Sdk"
call :check_sdk "%TOOLS%\android-sdk"
if defined SDK_DIR goto :s_done

set "SDK_DIR=%TOOLS%\android-sdk"
set "SDKM=%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat"
if exist "%SDKM%" goto :s_install

echo     未检测到完整的 Android SDK，开始下载 command-line tools ^(约 150MB^) ...
call :download "%CMDTOOLS_URL%" "%TOOLS%\cmdtools.zip" || goto :fail
call :unzip "%TOOLS%\cmdtools.zip" "%TOOLS%\_cmd_tmp" || goto :fail
if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools"
if exist "%SDK_DIR%\cmdline-tools\latest" rmdir /s /q "%SDK_DIR%\cmdline-tools\latest"
move "%TOOLS%\_cmd_tmp\cmdline-tools" "%SDK_DIR%\cmdline-tools\latest" >nul
rmdir /s /q "%TOOLS%\_cmd_tmp" 2>nul
del "%TOOLS%\cmdtools.zip" 2>nul
if not exist "%SDKM%" (
    echo [错误] 没找到 sdkmanager.bat
    goto :fail
)

:s_install
echo     接受 SDK 许可协议 ...
(for /l %%i in (1,1,40) do @echo y) | "%SDKM%" --sdk_root="%SDK_DIR%" --licenses >nul 2>&1
echo     安装 platforms;%PLATFORM% 和 build-tools;%BT_VER% ...
call "%SDKM%" --sdk_root="%SDK_DIR%" "platforms;%PLATFORM%" "build-tools;%BT_VER%"
if not exist "%SDK_DIR%\platforms\%PLATFORM%\android.jar" (
    echo [错误] Android 平台安装失败，请检查网络后重新运行。
    goto :fail
)

:s_done
set "ANDROID_HOME=%SDK_DIR%"
set "ANDROID_SDK_ROOT=%SDK_DIR%"
set "SDK_FWD=%SDK_DIR:\=/%"
> "%ROOT%\local.properties" echo sdk.dir=%SDK_FWD%
echo     OK  Android SDK: %SDK_DIR%
echo.

rem ---------------------------------------------------------
rem  [4/4] 编译
rem ---------------------------------------------------------
echo [4/4] 开始编译 debug APK ^(首次需要下载依赖库，可能要 5~15 分钟，请耐心等待^) ...
echo.
set "GRADLE_OPTS=-Dfile.encoding=UTF-8"
call "%GRADLE_BAT%" assembleDebug --no-daemon --console=plain
if errorlevel 1 (
    echo.
    echo [错误] 编译失败，请查看上面的报错信息。
    goto :fail
)

set "APK_SRC=%ROOT%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_SRC%" (
    echo [错误] 编译完成但没找到 app-debug.apk
    goto :fail
)
copy /y "%APK_SRC%" "%ROOT%\SgdMyrMonitor.apk" >nul

echo.
echo ==================================================
echo   成功！APK 已生成：
echo   %ROOT%\SgdMyrMonitor.apk
echo   传到手机安装即可 ^(需允许“安装未知应用”^)
echo ==================================================
explorer /select,"%ROOT%\SgdMyrMonitor.apk"
echo.
pause
exit /b 0

:fail
echo.
echo ---------- 失败，已停止 ----------
pause
exit /b 1


rem =========================================================
rem  子程序
rem =========================================================

:check_java
rem 参数1: java.exe 路径。JDK 17 或 21 且带 javac 才算合格 -> JAVA_OK=1
set "JAVA_OK=0"
set "JV="
"%~1" -version > "%TEMP%\sgd_jv.txt" 2>&1
for /f "usebackq tokens=3" %%v in (`findstr /i /c:" version " "%TEMP%\sgd_jv.txt"`) do if not defined JV set "JV=%%~v"
del "%TEMP%\sgd_jv.txt" >nul 2>nul
if not defined JV exit /b 0
set "JMAJ=%JV:~0,2%"
if "%JMAJ%"=="17" set "JAVA_OK=1"
if "%JMAJ%"=="21" set "JAVA_OK=1"
if not exist "%~dp1javac.exe" set "JAVA_OK=0"
if "%JAVA_OK%"=="1" echo     发现 JDK %JV%: %~1
if not "%JAVA_OK%"=="1" echo     跳过 Java %JV%: 版本不符或不是 JDK
exit /b 0

:check_gradle
rem 参数1: gradle.bat 路径。版本 8.7 及以上的 8.x 才算合格 -> GRADLE_OK=1
set "GRADLE_OK=0"
set "GV="
call "%~1" --version > "%TEMP%\sgd_gv.txt" 2>&1
for /f "usebackq tokens=2" %%v in (`findstr /b /c:"Gradle " "%TEMP%\sgd_gv.txt"`) do if not defined GV set "GV=%%v"
del "%TEMP%\sgd_gv.txt" >nul 2>nul
if not defined GV exit /b 0
set "GMAJ="
set "GMIN="
for /f "tokens=1,2 delims=." %%a in ("%GV%") do set "GMAJ=%%a" & set "GMIN=%%b"
if not defined GMIN set "GMIN=0"
if "%GMAJ%"=="8" if %GMIN% GEQ 7 set "GRADLE_OK=1"
if "%GRADLE_OK%"=="1" echo     发现 Gradle %GV%: %~1
if not "%GRADLE_OK%"=="1" echo     跳过 Gradle %GV%: 版本不在 8.7 ~ 8.x
exit /b 0

:check_sdk
rem 参数1: SDK 目录。含 platform 34 和 build-tools 34.0.0 才算合格 -> 设置 SDK_DIR
if defined SDK_DIR exit /b 0
if "%~1"=="" exit /b 0
if not exist "%~1\platforms\%PLATFORM%\android.jar" exit /b 0
if not exist "%~1\build-tools\%BT_VER%" exit /b 0
set "SDK_DIR=%~1"
exit /b 0

:download
rem 参数1: URL  参数2: 保存路径
set "DL_URL=%~1"
set "DL_OUT=%~2"
echo     下载: %~1
where curl.exe >nul 2>nul
if errorlevel 1 goto :dl_ps
curl.exe -L --fail --retry 3 --connect-timeout 20 -o "%DL_OUT%" "%DL_URL%"
if not errorlevel 1 exit /b 0
:dl_ps
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%DL_URL%' -OutFile '%DL_OUT%'"
if errorlevel 1 (
    echo [错误] 下载失败，请检查网络 ^(或代理^) 后重试。
    exit /b 1
)
exit /b 0

:unzip
rem 参数1: zip 文件  参数2: 解压到的目录
echo     解压 ...
if exist "%~2" rmdir /s /q "%~2"
mkdir "%~2"
tar.exe -xf "%~1" -C "%~2" >nul 2>nul
if not errorlevel 1 exit /b 0
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%~1' -DestinationPath '%~2' -Force"
if errorlevel 1 (
    echo [错误] 解压失败
    exit /b 1
)
exit /b 0

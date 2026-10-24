@echo off
echo ========================================================
echo  Running Prism Android Regression Test Suite
echo ========================================================
echo.
cd /d "%~dp0"
call gradlew.bat testDebugUnitTest --tests "com.secondjb.prism.PrismRegressionTestSuite" --console=plain
if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo  SUCCESS: All 26 Regression Tests Passed!
    echo ========================================================
) else (
    echo.
    echo ========================================================
    echo  FAILURE: One or more regression tests failed.
    echo ========================================================
)
pause

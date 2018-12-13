@ECHO off
SETLOCAL
IF [%1]==[open] (
 start .
 GOTO:EOF
)
IF [%1]==[clean] (
  IF NOT EXIST app_data  (
    echo  app_data does not exist
    GOTO:EOF
  )
  tree app_data
  echo ""
  rmdir app_data /s
  GOTO:EOF
)
IF [%1]==[jaunt] (
   COPY "%eclipse_workplace%\imports\jaunt\jaunt.jar" "%~dp0app/lib/jaunt.jar"
   GOTO:EOF
)
app\bin\RssScrapper.bat %*

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
   COPY "C:\Users\Sameer\Documents\MEGA\eclipse_workplace\imports\jaunt\jaunt.jar" "app/lib/jaunt.jar"
   GOTO:EOF
)
app\bin\RssScrapper.bat %*

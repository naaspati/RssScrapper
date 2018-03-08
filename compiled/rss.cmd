@ECHO off
SETLOCAL
IF [%1]==[open] (
 start .
) ELSE (
  IF [%1]==[jaunt] (
   COPY "C:\Users\Sameer\Documents\MEGA\eclipse_workplace\imports\jaunt\jaunt.jar" "app/lib/jaunt.jar"
  ) ELSE (
    app\bin\RssScrapper.bat %*
  )  
)

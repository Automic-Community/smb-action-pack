del .\output\*.xml /Q
del .\output\*.html /Q
del .\output\*.png /Q

set CLASSPATH="%~dp0\..\..\lib\itpa-keywords-0.0.1-SNAPSHOT.jar;%CLASSPATH%"

echo %CLASSPATH%

ride.py ./
mcproxy
=======

MediterraneanCoin proxy - necessary for mining MediterraneanCoins with cgminer

This project has been built using Netbeans 7.3.1



To run the project from the command line, go to the dist folder and
type the following:

java -jar mcproxy.jar

It is important that all the libraries are present in the "lib" subdirectory.

parameters:
-s: hostname of wallet/pool (default: localhost)
-p: port of wallet/pool (default: 9372)
-b: bind to local address (default: )
-l: local proxy port (default: 8080)
-a: use asynchronous http server (default: do not use)
-v: verbose

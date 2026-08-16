@echo off
start "" /min cmd /c "chcp 65001 > nul & mvn clean compile exec:java"

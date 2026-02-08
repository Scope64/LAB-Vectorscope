# Tuer les processus Java existants
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# Nettoyage
del *.class -ErrorAction SilentlyContinue

# Compilation
javac Scope64.java

# Creation JAR
jar cfm Scope64.jar manifest.txt *.class

# Obfuscation
# java -jar proguard.jar "@proguard.pro"

# Nettoyage
del *.class -ErrorAction SilentlyContinue

# Lancer la version
java -jar Scope64.jar
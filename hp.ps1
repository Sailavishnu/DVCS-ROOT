# Set paths
$DVCS_ROOT = "D:\project\DVCS-ROOT"
$javafxLib = "$env:JAVAFX_HOME\lib"

# Clean and recreate output folder to avoid stale .class files
Remove-Item -Recurse -Force "$DVCS_ROOT\client\out\*" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$DVCS_ROOT\client\out" | Out-Null

# Compile
javac --module-path "$javafxLib" `
--add-modules javafx.controls,javafx.fxml,javafx.swing `
-cp "$DVCS_ROOT\client\lib\*" `
-d "$DVCS_ROOT\client\out" `
(Get-ChildItem "$DVCS_ROOT\client\src\main\java" -Recurse -Filter *.java).FullName

if ($LASTEXITCODE -ne 0) { Write-Error "Compile failed. Fix errors above."; exit 1 }

# Run
java --enable-native-access=javafx.graphics `
--module-path "$javafxLib" `
--add-modules javafx.controls,javafx.fxml,javafx.swing `
-cp "$DVCS_ROOT\client\out;$DVCS_ROOT\client\src\main\resources;$DVCS_ROOT\client\lib\*" `
com.dvcs.client.MainApp
#!/usr/bin/env bash
# Builds MajestyMonitor.exe and the MSI installer (works on Linux or Windows/Git-Bash).
# Needs: .NET SDK 8+, python3, and msitools (wixl) for the MSI step.
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n 's:.*<Version>\(.*\)</Version>.*:\1:p' src/MajestyMonitor/MajestyMonitor.csproj)
OUT=src/MajestyMonitor/bin/Release/net472

echo "==> Protocol tests"
dotnet run -c Release --project tests/ProtocolTests

echo "==> Build $VERSION"
dotnet build -c Release src/MajestyMonitor/MajestyMonitor.csproj

echo "==> MSI"
mkdir -p dist
python3 installer/make_wxs.py "$OUT" "$VERSION" installer/MajestyMonitor.wxs
wixl -a x64 -o "dist/MAJESTY_Air_Cooler_Monitor_${VERSION}.msi" installer/MajestyMonitor.wxs
echo "Done: dist/MAJESTY_Air_Cooler_Monitor_${VERSION}.msi"

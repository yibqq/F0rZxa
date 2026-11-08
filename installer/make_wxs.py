"""Generates the WiX source for the MSI from the build output folder.

usage: make_wxs.py <bin_dir> <version> <out.wxs>
"""
import hashlib
import os
import sys
import uuid
from xml.sax.saxutils import quoteattr

UPGRADE_CODE = "7C9E2B41-5D3A-4E8F-9B6C-2A1D4F3E8B70"  # never change: lets new versions upgrade old ones
NS = uuid.UUID("3f0a9a52-8e8b-4b38-9a0e-6f9c1b2d4e11")

bin_dir, version, out = sys.argv[1], sys.argv[2], sys.argv[3]

SKIP_EXT = {".pdb", ".xml"}
files = sorted(f for f in os.listdir(bin_dir)
               if os.path.isfile(os.path.join(bin_dir, f)) and os.path.splitext(f)[1].lower() not in SKIP_EXT)


def ident(prefix, name):
    return prefix + hashlib.md5(name.encode()).hexdigest()[:16].upper()


def guid(name):
    return str(uuid.uuid5(NS, name)).upper()


comps = []
refs = []
for f in files:
    cid = ident("C", f)
    fid = "MainExe" if f == "MajestyMonitor.exe" else ident("F", f)
    src = os.path.abspath(os.path.join(bin_dir, f))
    comps.append(f'''          <Component Id="{cid}" Guid="{guid(f)}" Win64="yes">
            <File Id="{fid}" Name={quoteattr(f)} Source={quoteattr(src)} KeyPath="yes" />
          </Component>''')
    refs.append(f'      <ComponentRef Id="{cid}" />')

icon = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src", "MajestyMonitor", "Assets", "app.ico"))

wxs = f'''<?xml version="1.0" encoding="utf-8"?>
<Wix xmlns="http://schemas.microsoft.com/wix/2006/wi">
  <Product Id="*" Name="MAJESTY Air Cooler Monitor" Language="1033" Version="{version}"
           Manufacturer="MAJESTY" UpgradeCode="{UPGRADE_CODE}">
    <Package Id="*" InstallerVersion="500" Compressed="yes" InstallScope="perMachine"
             Description="MAJESTY Air Cooler Monitor {version}" Manufacturer="MAJESTY" />
    <Media Id="1" Cabinet="app.cab" EmbedCab="yes" />

    <Icon Id="AppIcon" SourceFile={quoteattr(icon)} />
    <Property Id="ARPPRODUCTICON" Value="AppIcon" />
    <Property Id="ARPHELPLINK" Value="https://github.com/yibqq/F0rZxa" />
    <Property Id="ARPNOREPAIR" Value="1" />

    <!-- Replace any earlier version of this app. -->
    <Upgrade Id="{UPGRADE_CODE}">
      <UpgradeVersion Minimum="0.0.0" Maximum="{version}" IncludeMinimum="yes" IncludeMaximum="no" Property="OLDERVERSIONFOUND" />
      <UpgradeVersion Minimum="{version}" IncludeMinimum="no" OnlyDetect="yes" Property="NEWERVERSIONFOUND" />
    </Upgrade>


    <Directory Id="TARGETDIR" Name="SourceDir">
      <Directory Id="ProgramFiles64Folder">
        <Directory Id="VendorFolder" Name="MAJESTY">
          <Directory Id="INSTALLFOLDER" Name="Air Cooler Monitor">
{chr(10).join(comps)}
          </Directory>
        </Directory>
      </Directory>
      <Directory Id="ProgramMenuFolder">
        <Directory Id="AppMenuFolder" Name="MAJESTY">
          <Component Id="CStartMenu" Guid="{guid('startmenu')}" Win64="yes">
            <Shortcut Id="StartMenuShortcut" Name="MAJESTY Air Cooler Monitor" Description="MAJESTY Air Cooler Monitor"
                      Target="[INSTALLFOLDER]MajestyMonitor.exe" WorkingDirectory="INSTALLFOLDER" Icon="AppIcon" />
            <RemoveFolder Id="RemoveAppMenuFolder" On="uninstall" />
            <RegistryValue Root="HKCU" Key="Software\\MAJESTY\\AirCoolerMonitor" Name="StartMenuShortcut" Type="integer" Value="1" KeyPath="yes" />
          </Component>
        </Directory>
      </Directory>
      <Directory Id="DesktopFolder" Name="Desktop">
        <Component Id="CDesktop" Guid="{guid('desktop')}" Win64="yes">
          <Shortcut Id="DesktopShortcut" Name="MAJESTY Air Cooler Monitor" Description="MAJESTY Air Cooler Monitor"
                    Target="[INSTALLFOLDER]MajestyMonitor.exe" WorkingDirectory="INSTALLFOLDER" Icon="AppIcon" />
          <RegistryValue Root="HKCU" Key="Software\\MAJESTY\\AirCoolerMonitor" Name="DesktopShortcut" Type="integer" Value="1" KeyPath="yes" />
        </Component>
      </Directory>
    </Directory>

    <Feature Id="Main" Title="MAJESTY Air Cooler Monitor" Level="1">
{chr(10).join(refs)}
      <ComponentRef Id="CStartMenu" />
      <ComponentRef Id="CDesktop" />
    </Feature>

    <!-- Remove the "start with Windows" scheduled task when the app is uninstalled. -->
    <CustomAction Id="CleanupAutoStart" FileKey="MainExe" ExeCommand="--uninstall-cleanup" Execute="deferred" Impersonate="no" Return="ignore" />
    <!-- Close a running copy before files are replaced or removed. -->
    <CustomAction Id="CloseRunningApp" FileKey="MainExe" ExeCommand="--close-running" Execute="deferred" Impersonate="no" Return="ignore" />

    <InstallExecuteSequence>
      <Custom Action="CloseRunningApp" After="InstallInitialize">Installed OR OLDERVERSIONFOUND</Custom>
      <RemoveExistingProducts After="CloseRunningApp" />
      <Custom Action="CleanupAutoStart" Before="RemoveFiles">REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE</Custom>
    </InstallExecuteSequence>
  </Product>
</Wix>
'''
with open(out, "w", encoding="utf-8") as fh:
    fh.write(wxs)
print(f"{out}: {len(files)} files")

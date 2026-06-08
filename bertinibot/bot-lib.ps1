# BertiniBot V2 - shared functions used by both bot.ps1 (CLI) and
# bot-gui.ps1 (Windows Forms panel). This file defines only functions; it
# performs no actions on load, so it is safe to dot-source from anywhere.

$script:BotRoot = $PSScriptRoot
$script:TaskName = 'BertiniBot'
$script:JarPath = Join-Path $script:BotRoot 'target\bertinibot-2.0.0.jar'
$script:PanelJarPath = Join-Path $script:BotRoot 'target\bertinibot-2.0.0-panel.jar'
$script:WorkDir = $script:BotRoot
$script:LogFile = Join-Path $script:BotRoot 'logs\bertinibot.log'
$script:ShortcutPath = Join-Path $script:BotRoot 'BertiniBot Panel.lnk'

function Get-BotPaths {
    [PSCustomObject]@{
        Root         = $script:BotRoot
        TaskName     = $script:TaskName
        JarPath      = $script:JarPath
        PanelJarPath = $script:PanelJarPath
        WorkDir      = $script:WorkDir
        LogFile      = $script:LogFile
        ShortcutPath = $script:ShortcutPath
    }
}

function Find-Java {
    # Prefer javaw.exe over java.exe: javaw.exe is the windowless variant
    # of the JVM (no attached console), perfect for running the bot as a
    # background service without showing a terminal.
    $patterns = @(
        'C:\Program Files\Eclipse Adoptium\jdk-21*\bin\javaw.exe',
        'C:\Program Files\Java\jdk-21*\bin\javaw.exe',
        'C:\Program Files\Eclipse Adoptium\jdk-*\bin\javaw.exe'
    )
    foreach ($pattern in $patterns) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    # Fall back to PATH java(.exe) but try to swap it for the javaw.exe
    # sitting next to it, since that's still console-less.
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $javaw = $cmd.Source -replace 'java\.exe$', 'javaw.exe'
        if (Test-Path $javaw) { return $javaw }
        return $cmd.Source
    }

    $patternsJava = @(
        'C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe',
        'C:\Program Files\Java\jdk-21*\bin\java.exe',
        'C:\Program Files\Eclipse Adoptium\jdk-*\bin\java.exe'
    )
    foreach ($pattern in $patternsJava) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    throw "No se encuentra java.exe ni javaw.exe. Instala JDK 21 (Eclipse Adoptium) o anyadelo al PATH."
}

function Get-BotProcess {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and ($_.CommandLine -like '*bertinibot*') }
}

function Stop-Bot {
    $procs = @(Get-BotProcess)
    if ($procs.Count -eq 0) { return @{ Killed = 0; Message = 'No hay ninguna instancia del bot corriendo.' } }

    # 1) Ask the bot for a graceful shutdown via its admin HTTP endpoint.
    #    The bot disconnects from Discord cleanly, so the presence in the
    #    server goes offline immediately (instead of ~60s of cache).
    $gracefulOk = $false
    try {
        $resp = Invoke-WebRequest -Uri 'http://127.0.0.1:8765/shutdown' -Method POST `
            -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $gracefulOk = $true
            # Wait up to ~10s for the JVM to actually exit by itself.
            for ($i = 0; $i -lt 20; $i++) {
                Start-Sleep -Milliseconds 500
                if (-not (Get-BotProcess)) { break }
            }
        }
    } catch {
        # Endpoint not reachable: older build or already shutting down.
    }

    # 2) Any process still alive gets the hammer (fallback).
    $remaining = @(Get-BotProcess)
    foreach ($p in $remaining) {
        try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop } catch {}
    }
    if ($remaining.Count -gt 0) { Start-Sleep -Milliseconds 800 }

    $msg = if ($gracefulOk) {
        "Detenidas $($procs.Count) instancia(s) (shutdown limpio en Discord)."
    } else {
        "Detenidas $($procs.Count) instancia(s) (kill forzado: Discord puede tardar ~1 min en marcarlo offline)."
    }
    @{ Killed = $procs.Count; Graceful = $gracefulOk; Message = $msg }
}

function Start-Bot {
    if (-not (Test-Path $script:JarPath)) {
        throw "No existe el JAR: $script:JarPath. Compila primero con: .\mvnw.cmd -B -ntp -DskipTests package"
    }
    if (Get-BotProcess) { return @{ Started = $false; Message = 'El bot ya esta corriendo.' } }

    # Always launch via Start-Process with javaw.exe so no terminal window
    # is attached, regardless of whether the scheduled task exists. The
    # scheduled task is only used as the boot trigger at user login.
    # (javaw.exe is already a windowless binary, so no -WindowStyle is needed.)
    $java = Find-Java
    Start-Process -FilePath $java -ArgumentList @('-jar', "`"$script:JarPath`"") `
        -WorkingDirectory $script:WorkDir | Out-Null

    Start-Sleep -Seconds 2
    if (Get-BotProcess) {
        return @{ Started = $true; Message = 'Bot arrancado correctamente.' }
    }
    return @{ Started = $false; Message = 'El bot no parece haber arrancado. Revisa los logs.' }
}

function Restart-Bot {
    [void](Stop-Bot)
    Start-Sleep -Milliseconds 500
    Start-Bot
}

function Install-Task {
    $java = Find-Java
    $action = New-ScheduledTaskAction -Execute $java `
        -Argument "-jar `"$script:JarPath`"" -WorkingDirectory $script:WorkDir
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME `
        -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -ExecutionTimeLimit (New-TimeSpan -Days 0) `
        -RestartCount 5 `
        -RestartInterval (New-TimeSpan -Minutes 1) `
        -StartWhenAvailable

    Register-ScheduledTask -TaskName $script:TaskName -Action $action -Trigger $trigger `
        -Principal $principal -Settings $settings -Force | Out-Null
    @{ Installed = $true; Java = $java }
}

function Uninstall-Task {
    $task = Get-ScheduledTask -TaskName $script:TaskName -ErrorAction SilentlyContinue
    if (-not $task) { return @{ Uninstalled = $false; Message = 'No habia ninguna tarea registrada.' } }
    Unregister-ScheduledTask -TaskName $script:TaskName -Confirm:$false
    @{ Uninstalled = $true; Message = 'Tarea programada eliminada.' }
}

function Test-TaskNeedsUpgrade {
    # Returns $true if the scheduled task is registered but still uses
    # java.exe (which leaves a console window open) instead of javaw.exe.
    $task = Get-ScheduledTask -TaskName $script:TaskName -ErrorAction SilentlyContinue
    if (-not $task) { return $false }
    foreach ($a in $task.Actions) {
        if ($a.Execute -and $a.Execute -match 'java\.exe$') { return $true }
    }
    return $false
}

function New-PanelShortcut {
    # Create (or refresh) "BertiniBot Panel.lnk" in the bot root, pointing
    # straight at javaw.exe -jar target\bertinibot-*-panel.jar. Because
    # javaw.exe is GUI subsystem, double-clicking the shortcut launches
    # the panel with zero terminal flash and nothing for antivirus to flag
    # (no .vbs / no -ExecutionPolicy Bypass / no hidden window tricks).
    #
    # If the user dropped an icon.ico in the bot root we use it as the
    # shortcut icon; otherwise we fall back to javaw's own icon.
    if (-not (Test-Path $script:PanelJarPath)) {
        throw "No existe el panel JAR: $script:PanelJarPath. Compila primero con: .\mvnw.cmd -B -ntp -DskipTests package"
    }
    $java = Find-Java
    $customIco = Join-Path $script:BotRoot 'icon.ico'
    $iconLocation = if (Test-Path $customIco) { "$customIco,0" } else { "$java,0" }
    $iconKind = if (Test-Path $customIco) { 'icon.ico personalizado' } else { 'icono de javaw (por defecto)' }

    # Force-recreate the .lnk: editing in place sometimes leaves Explorer
    # showing the previous icon out of cache.
    if (Test-Path $script:ShortcutPath) { Remove-Item -Force $script:ShortcutPath }

    $shell = New-Object -ComObject WScript.Shell
    $sc = $shell.CreateShortcut($script:ShortcutPath)
    $sc.TargetPath = $java
    $sc.Arguments = "-jar `"$script:PanelJarPath`""
    $sc.WorkingDirectory = $script:WorkDir
    $sc.WindowStyle = 1  # Normal window (the GUI). javaw never opens a console.
    $sc.Description = 'BertiniBot V2 - Control Panel'
    $sc.IconLocation = $iconLocation
    $sc.Save()
    @{ Created = $true; Path = $script:ShortcutPath; Java = $java; Icon = $iconKind }
}

function Start-Panel {
    if (-not (Test-Path $script:PanelJarPath)) {
        throw "No existe el panel JAR: $script:PanelJarPath. Compila primero con: .\mvnw.cmd -B -ntp -DskipTests package"
    }
    $java = Find-Java
    Start-Process -FilePath $java -ArgumentList @('-jar', "`"$script:PanelJarPath`"") `
        -WorkingDirectory $script:WorkDir | Out-Null
}

function Get-BotStatus {
    $task = Get-ScheduledTask -TaskName $script:TaskName -ErrorAction SilentlyContinue
    $procs = @(Get-BotProcess)
    $proc = if ($procs.Count -gt 0) { Get-Process -Id $procs[0].ProcessId -ErrorAction SilentlyContinue } else { $null }

    [PSCustomObject]@{
        Running         = $procs.Count -gt 0
        Pid             = if ($procs.Count -gt 0) { $procs[0].ProcessId } else { $null }
        MemoryMB        = if ($proc) { [Math]::Round($proc.WorkingSet64 / 1MB, 1) } else { $null }
        StartTime       = if ($proc) { $proc.StartTime } else { $null }
        TaskInstalled   = [bool]$task
        TaskState       = if ($task) { $task.State.ToString() } else { 'NotInstalled' }
        LastTaskResult  = if ($task) { (Get-ScheduledTaskInfo -TaskName $script:TaskName).LastTaskResult } else { $null }
    }
}

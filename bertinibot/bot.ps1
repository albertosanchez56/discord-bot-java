# BertiniBot V2 - service-style CLI for Windows.
#
# Wraps the bot as a Windows Scheduled Task that starts at user login (no
# admin rights required, no need to store passwords).
#
#   .\bot.ps1 install     register the auto-start task, create the panel
#                         shortcut, and start the bot
#   .\bot.ps1 uninstall   remove the task (does NOT stop a running bot)
#   .\bot.ps1 start       start the bot
#   .\bot.ps1 stop        stop the bot
#   .\bot.ps1 restart     stop + start
#   .\bot.ps1 status      show task & process status
#   .\bot.ps1 logs        tail logs in real time (Ctrl+C to exit)
#   .\bot.ps1 gui         open the native Java Swing panel (no terminal)
#   .\bot.ps1 shortcut    (re-)create "BertiniBot Panel.lnk" on the bot root

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('install', 'uninstall', 'start', 'stop', 'restart', 'status', 'logs', 'gui', 'shortcut')]
    [string]$Command
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
. (Join-Path $PSScriptRoot 'bot-lib.ps1')

switch ($Command) {
    'install' {
        $r = Install-Task
        Write-Host "Java detectado: $($r.Java)" -ForegroundColor Cyan
        Write-Host "Scheduled Task registrada. Se arrancara automaticamente al iniciar sesion." -ForegroundColor Green
        # Create the panel shortcut so the user has a 1-click way to open
        # the GUI from Explorer without any antivirus-flagging shenanigans.
        try {
            $sc = New-PanelShortcut
            Write-Host "Acceso directo creado: $($sc.Path)" -ForegroundColor Green
        } catch {
            Write-Host "Aviso: no se pudo crear el acceso directo del panel: $($_.Exception.Message)" -ForegroundColor Yellow
        }
        $s = Start-Bot
        $color = if ($s.Started -or $s.Message -match 'ya esta') { 'Green' } else { 'Red' }
        Write-Host $s.Message -ForegroundColor $color
    }
    'shortcut' {
        $sc = New-PanelShortcut
        Write-Host "Acceso directo creado/actualizado: $($sc.Path)" -ForegroundColor Green
        Write-Host "  Apunta a: $($sc.Java) -jar (panel jar)" -ForegroundColor Cyan
        Write-Host "  Icono:    $($sc.Icon)" -ForegroundColor Cyan
        if ($sc.Icon -notlike '*personalizado*') {
            Write-Host ""
            Write-Host "Para usar tu propio icono:" -ForegroundColor Yellow
            Write-Host "  1) Copia un fichero icon.ico en la carpeta del bot."
            Write-Host "  2) (Opcional) Copia tambien un icon.png para la ventana del panel."
            Write-Host "  3) Ejecuta de nuevo: .\bot.ps1 shortcut"
        }
    }
    'uninstall' {
        $r = Uninstall-Task
        Write-Host $r.Message -ForegroundColor Green
    }
    'start' {
        $r = Start-Bot
        $color = if ($r.Started -or $r.Message -match 'ya esta') { 'Green' } else { 'Red' }
        Write-Host $r.Message -ForegroundColor $color
    }
    'stop' {
        $r = Stop-Bot
        Write-Host $r.Message -ForegroundColor Green
    }
    'restart' {
        $r = Restart-Bot
        $color = if ($r.Started) { 'Green' } else { 'Red' }
        Write-Host $r.Message -ForegroundColor $color
    }
    'status' {
        $s = Get-BotStatus
        $paths = Get-BotPaths
        Write-Host "Scheduled Task '$($paths.TaskName)':" -ForegroundColor Cyan
        if ($s.TaskInstalled) {
            Write-Host ("  Estado:        {0}" -f $s.TaskState)
            Write-Host ("  Ultimo result: 0x{0:X8} ({0})" -f $s.LastTaskResult)
        } else {
            Write-Host "  no instalada" -ForegroundColor Yellow
        }
        Write-Host ""
        if ($s.Running) {
            Write-Host "Proceso del bot:" -ForegroundColor Cyan
            Write-Host ("  PID {0}  iniciado {1}  memoria {2} MB" -f $s.Pid, $s.StartTime, $s.MemoryMB)
        } else {
            Write-Host "Proceso del bot: NO esta corriendo." -ForegroundColor Yellow
        }
    }
    'logs' {
        $paths = Get-BotPaths
        if (-not (Test-Path $paths.LogFile)) {
            Write-Host "Aun no hay log en $($paths.LogFile). Arranca el bot primero." -ForegroundColor Yellow
            return
        }
        Write-Host "Siguiendo $($paths.LogFile) (Ctrl+C para salir)..." -ForegroundColor Cyan
        Get-Content -Path $paths.LogFile -Wait -Tail 50
    }
    'gui' {
        # New native panel: javaw -jar bertinibot-*-panel.jar. Zero terminal
        # involved (javaw is a GUI subsystem binary), zero PowerShell flags
        # antivirus engines flag, zero VBScript. Falls back to a clear error
        # if the project hasn't been built yet.
        Start-Panel
        Write-Host "Panel arrancado." -ForegroundColor Green
    }
}

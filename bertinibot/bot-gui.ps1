# BertiniBot V2 - Windows Forms control panel.
#
# A small dark-themed GUI to start / stop / restart the bot, toggle the
# auto-start scheduled task, and tail the live log without juggling shells.
# Launches without an attached console window when invoked via .\bot.ps1 gui.

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
. (Join-Path $PSScriptRoot 'bot-lib.ps1')

# Hide our own PowerShell console window (if launched from a .cmd or
# directly): much friendlier than -WindowStyle Hidden + ExecutionPolicy
# Bypass on the command line, which antivirus tools tend to flag.
try {
    if (-not ('BertiniBotWinApi' -as [type])) {
        Add-Type -Name BertiniBotWinApi -Namespace Win32 -MemberDefinition @'
            [System.Runtime.InteropServices.DllImport("kernel32.dll")]
            public static extern System.IntPtr GetConsoleWindow();
            [System.Runtime.InteropServices.DllImport("user32.dll")]
            public static extern bool ShowWindow(System.IntPtr hWnd, int nCmdShow);
'@
    }
    $h = [Win32.BertiniBotWinApi]::GetConsoleWindow()
    if ($h -ne [IntPtr]::Zero) {
        [void][Win32.BertiniBotWinApi]::ShowWindow($h, 0) # SW_HIDE
    }
} catch { }

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

# ----- Palette -------------------------------------------------------------
$Palette = @{
    Bg        = [System.Drawing.Color]::FromArgb(24, 26, 31)
    Panel     = [System.Drawing.Color]::FromArgb(34, 37, 44)
    LogBg     = [System.Drawing.Color]::FromArgb(18, 20, 24)
    Text      = [System.Drawing.Color]::FromArgb(220, 222, 226)
    Muted     = [System.Drawing.Color]::FromArgb(155, 160, 170)
    Green     = [System.Drawing.Color]::FromArgb(46, 204, 113)
    Red       = [System.Drawing.Color]::FromArgb(231, 76, 60)
    Yellow    = [System.Drawing.Color]::FromArgb(241, 196, 15)
    Blue      = [System.Drawing.Color]::FromArgb(52, 152, 219)
    Gray      = [System.Drawing.Color]::FromArgb(99, 105, 117)
    Border    = [System.Drawing.Color]::FromArgb(60, 65, 75)
}

# ----- Form ---------------------------------------------------------------
$form = New-Object System.Windows.Forms.Form
$form.Text = 'BertiniBot V2 - Control Panel'
$form.Size = New-Object System.Drawing.Size(960, 660)
$form.MinimumSize = New-Object System.Drawing.Size(720, 480)
$form.StartPosition = 'CenterScreen'
$form.BackColor = $Palette.Bg
$form.ForeColor = $Palette.Text
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)

# Try to use the JDA icon if there is one (silently ignore if missing)
try {
    $icoPath = Join-Path $PSScriptRoot 'src\main\resources\icon.ico'
    if (Test-Path $icoPath) { $form.Icon = New-Object System.Drawing.Icon($icoPath) }
} catch { }

# ----- Header panel --------------------------------------------------------
$header = New-Object System.Windows.Forms.Panel
$header.Dock = 'Top'
$header.Height = 84
$header.BackColor = $Palette.Panel
$form.Controls.Add($header)

$statusDot = New-Object System.Windows.Forms.Label
$statusDot.Location = New-Object System.Drawing.Point(20, 22)
$statusDot.Size = New-Object System.Drawing.Size(24, 24)
$statusDot.Font = New-Object System.Drawing.Font('Segoe UI', 22, [System.Drawing.FontStyle]::Bold)
$statusDot.Text = [char]9679       # bullet
$statusDot.ForeColor = $Palette.Gray
$statusDot.TextAlign = 'MiddleCenter'
$header.Controls.Add($statusDot)

$statusTitle = New-Object System.Windows.Forms.Label
$statusTitle.Location = New-Object System.Drawing.Point(54, 14)
$statusTitle.Size = New-Object System.Drawing.Size(700, 28)
$statusTitle.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 14, [System.Drawing.FontStyle]::Bold)
$statusTitle.Text = 'Comprobando estado...'
$statusTitle.ForeColor = $Palette.Text
$header.Controls.Add($statusTitle)

$statusDetail = New-Object System.Windows.Forms.Label
$statusDetail.Location = New-Object System.Drawing.Point(54, 46)
$statusDetail.Size = New-Object System.Drawing.Size(800, 22)
$statusDetail.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$statusDetail.Text = ''
$statusDetail.ForeColor = $Palette.Muted
$header.Controls.Add($statusDetail)

# ----- Toolbar -------------------------------------------------------------
$toolbar = New-Object System.Windows.Forms.Panel
$toolbar.Dock = 'Top'
$toolbar.Height = 60
$toolbar.BackColor = $Palette.Bg
$form.Controls.Add($toolbar)

function New-FlatButton {
    param([string]$Text, [int]$X, [int]$Width, [System.Drawing.Color]$Color, [ScriptBlock]$OnClick)
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $Text
    $b.Location = New-Object System.Drawing.Point($X, 12)
    $b.Size = New-Object System.Drawing.Size($Width, 36)
    $b.FlatStyle = 'Flat'
    $b.BackColor = $Color
    $b.ForeColor = [System.Drawing.Color]::White
    $b.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10)
    $b.FlatAppearance.BorderSize = 0
    $b.Cursor = 'Hand'
    $b.Add_Click($OnClick)
    return $b
}

# Buttons will be re-bound below after Update-Status / Update-Logs are defined.
$btnStart     = New-FlatButton '> Start'         15  100 $Palette.Green  ({})
$btnStop      = New-FlatButton 'Stop'            125 100 $Palette.Red    ({})
$btnRestart   = New-FlatButton 'Restart'         235 100 $Palette.Yellow ({})
$btnLogs      = New-FlatButton 'Carpeta logs'    345 120 $Palette.Gray   ({})
$btnAutostart = New-FlatButton 'Auto-arranque'   475 150 $Palette.Blue   ({})
$btnClear     = New-FlatButton 'Limpiar vista'   635 130 $Palette.Gray   ({})

$toolbar.Controls.AddRange(@($btnStart, $btnStop, $btnRestart, $btnLogs, $btnAutostart, $btnClear))

# ----- Log box -------------------------------------------------------------
$logBox = New-Object System.Windows.Forms.RichTextBox
$logBox.Dock = 'Fill'
$logBox.ReadOnly = $true
$logBox.BackColor = $Palette.LogBg
$logBox.ForeColor = $Palette.Text
$logBox.Font = New-Object System.Drawing.Font('Consolas', 9.5)
$logBox.WordWrap = $false
$logBox.BorderStyle = 'None'
$logBox.DetectUrls = $false
$form.Controls.Add($logBox)
$logBox.BringToFront()

# Footer with small hint
$footer = New-Object System.Windows.Forms.Label
$footer.Dock = 'Bottom'
$footer.Height = 24
$footer.BackColor = $Palette.Panel
$footer.ForeColor = $Palette.Muted
$footer.TextAlign = 'MiddleRight'
$footer.Padding = New-Object System.Windows.Forms.Padding(0, 0, 12, 0)
$footer.Text = "Logs en vivo desde $((Get-BotPaths).LogFile)"
$footer.Font = New-Object System.Drawing.Font('Segoe UI', 8)
$form.Controls.Add($footer)

# ----- State for log tail --------------------------------------------------
$script:lastLogSize = 0L

function Update-Logs {
    $paths = Get-BotPaths
    if (-not (Test-Path $paths.LogFile)) { return }
    try {
        $fi = Get-Item $paths.LogFile -ErrorAction Stop
    } catch { return }
    if ($fi.Length -lt $script:lastLogSize) {
        # Log rotated; reset and reload tail.
        $script:lastLogSize = 0L
        $logBox.Clear()
    }
    if ($fi.Length -eq $script:lastLogSize) { return }

    try {
        $stream = [System.IO.File]::Open($paths.LogFile, 'Open', 'Read', 'ReadWrite')
        try {
            $stream.Seek($script:lastLogSize, [System.IO.SeekOrigin]::Begin) | Out-Null
            $reader = New-Object System.IO.StreamReader($stream)
            $newContent = $reader.ReadToEnd()
            $reader.Close()
        } finally {
            $stream.Close()
        }
    } catch {
        return
    }

    $script:lastLogSize = $fi.Length
    if ($newContent) {
        $logBox.AppendText($newContent)
        $logBox.SelectionStart = $logBox.TextLength
        $logBox.ScrollToCaret()
    }
}

function Update-Status {
    $s = Get-BotStatus
    if ($s.Running) {
        $statusDot.ForeColor = $Palette.Green
        $statusTitle.Text = 'ONLINE'
        $statusTitle.ForeColor = $Palette.Green
        $detail = "PID $($s.Pid)   |   $($s.MemoryMB) MB"
        if ($s.StartTime) {
            $up = (Get-Date) - $s.StartTime
            $detail += "   |   uptime $([int]$up.TotalHours)h $($up.Minutes)m $($up.Seconds)s"
        }
    } else {
        $statusDot.ForeColor = $Palette.Red
        $statusTitle.Text = 'DETENIDO'
        $statusTitle.ForeColor = $Palette.Red
        $detail = 'El bot no esta corriendo.'
    }
    if ($s.TaskInstalled) {
        $detail += "   |   Auto-arranque: ACTIVO"
        $btnAutostart.Text = 'Desactivar autostart'
        $btnAutostart.BackColor = $Palette.Gray
    } else {
        $detail += "   |   Auto-arranque: inactivo"
        $btnAutostart.Text = 'Activar autostart'
        $btnAutostart.BackColor = $Palette.Blue
    }
    $statusDetail.Text = $detail
}

function Show-Toast {
    param([string]$Message, [System.Drawing.Color]$Color = $Palette.Muted)
    $footer.Text = $Message
    $footer.ForeColor = $Color
}

# ----- Button actions ------------------------------------------------------
$btnStart.Add_Click({
    Show-Toast 'Arrancando...' $Palette.Yellow
    try { $r = Start-Bot; Show-Toast $r.Message $Palette.Green } catch { Show-Toast $_.Exception.Message $Palette.Red }
    Update-Status
})

$btnStop.Add_Click({
    Show-Toast 'Parando...' $Palette.Yellow
    try { $r = Stop-Bot; Show-Toast $r.Message $Palette.Green } catch { Show-Toast $_.Exception.Message $Palette.Red }
    Update-Status
})

$btnRestart.Add_Click({
    Show-Toast 'Reiniciando...' $Palette.Yellow
    try { $r = Restart-Bot; Show-Toast $r.Message $Palette.Green } catch { Show-Toast $_.Exception.Message $Palette.Red }
    Update-Status
})

$btnLogs.Add_Click({
    $paths = Get-BotPaths
    $logDir = Split-Path $paths.LogFile -Parent
    if (-not (Test-Path $logDir)) { New-Item -Path $logDir -ItemType Directory -Force | Out-Null }
    Start-Process explorer $logDir
})

$btnAutostart.Add_Click({
    $s = Get-BotStatus
    try {
        if ($s.TaskInstalled) {
            $r = Uninstall-Task
            Show-Toast 'Auto-arranque desactivado.' $Palette.Yellow
        } else {
            $r = Install-Task
            Show-Toast "Auto-arranque activado (Java: $($r.Java))." $Palette.Green
        }
    } catch {
        Show-Toast $_.Exception.Message $Palette.Red
    }
    Update-Status
})

$btnClear.Add_Click({
    $logBox.Clear()
    Show-Toast 'Vista de logs limpiada (el archivo no se ha tocado).' $Palette.Muted
})

# ----- Timers --------------------------------------------------------------
$timerLogs = New-Object System.Windows.Forms.Timer
$timerLogs.Interval = 750
$timerLogs.Add_Tick({ Update-Logs })

$timerStatus = New-Object System.Windows.Forms.Timer
$timerStatus.Interval = 2500
$timerStatus.Add_Tick({ Update-Status })

$form.Add_Shown({
    # Auto-fix: if the scheduled task is still pointing at java.exe (old
    # config that showed a console window), silently re-register it with
    # javaw.exe so the user never sees the terminal again.
    if (Test-TaskNeedsUpgrade) {
        try {
            [void](Install-Task)
            Show-Toast 'Auto-arranque actualizado a javaw.exe (sin consola).' $Palette.Blue
        } catch {
            Show-Toast "No se pudo actualizar autostart: $($_.Exception.Message)" $Palette.Red
        }
    }
    Update-Status
    Update-Logs
    $timerLogs.Start()
    $timerStatus.Start()
    $form.Activate()
})

$form.Add_FormClosing({
    $timerLogs.Stop()
    $timerStatus.Stop()
})

[void]$form.ShowDialog()

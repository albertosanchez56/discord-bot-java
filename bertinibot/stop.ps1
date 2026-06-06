# Stops every BertiniBot V2 java.exe instance that may have been left running.
# Useful after closing Cursor / the launching shell, since on Windows the JVM
# becomes an orphan instead of terminating with its parent.

$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*bertinibot*' }

if (-not $procs) {
    Write-Host "No BertiniBot process is running." -ForegroundColor Green
    exit 0
}

foreach ($p in $procs) {
    Write-Host ("Killing PID {0} ..." -f $p.ProcessId) -ForegroundColor Yellow
    Stop-Process -Id $p.ProcessId -Force
}

Write-Host "Done." -ForegroundColor Green

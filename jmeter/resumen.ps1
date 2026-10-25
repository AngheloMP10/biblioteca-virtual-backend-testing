# Consolida results\<escenario>\vus-<N>\report\statistics.json (y docker-stats.csv, si existe)
# en results\resumen.csv. Columnas: promedio, mediana, p90/p95/p99, maximo, throughput (req/s),
# % errores, L = lambda * W (Ley de Little) y CPU (% de un nucleo) de backend y PostgreSQL.
param(
    [string]$ResultsDir = "$PSScriptRoot\results"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path $ResultsDir).Path
$inv = [System.Globalization.CultureInfo]::InvariantCulture

function Get-CpuStats($rows) {
    $vals = @($rows | ForEach-Object { [double]::Parse(($_.cpu -replace '%', ''), $inv) })
    if ($vals.Count -eq 0) { return $null }
    $m = $vals | Measure-Object -Average -Maximum
    return [pscustomobject]@{ avg = [math]::Round($m.Average, 1); max = [math]::Round($m.Maximum, 1) }
}

$rows = foreach ($f in Get-ChildItem $root -Recurse -Filter statistics.json) {
    # Ruta relativa: <escenario>\vus-<N>\report\statistics.json
    $parts = $f.FullName.Substring($root.Length).Trim('\').Split('\')
    $scenario = $parts[0]
    $vus = [int]($parts[1] -replace 'vus-', '')
    $t = (Get-Content $f.FullName -Raw | ConvertFrom-Json).Total

    $statsFile = Join-Path (Split-Path $f.DirectoryName -Parent) 'docker-stats.csv'
    $back = $null
    $pg = $null
    if (Test-Path $statsFile) {
        $st = Import-Csv $statsFile -Header ts, name, cpu, mem
        $back = Get-CpuStats ($st | Where-Object { $_.name -eq 'biblioteca-backend' })
        $pg = Get-CpuStats ($st | Where-Object { $_.name -eq 'biblioteca-postgres' })
    }

    [pscustomobject]@{
        escenario   = $scenario
        vus         = $vus
        muestras    = $t.sampleCount
        errores_pct = [math]::Round($t.errorPct, 2)
        prom_ms     = [math]::Round($t.meanResTime, 1)
        mediana_ms  = [math]::Round($t.medianResTime, 1)
        p90_ms      = [math]::Round($t.pct1ResTime, 1)
        p95_ms      = [math]::Round($t.pct2ResTime, 1)
        p99_ms      = [math]::Round($t.pct3ResTime, 1)
        max_ms      = [math]::Round($t.maxResTime, 1)
        rps         = [math]::Round($t.throughput, 2)
        L_little    = [math]::Round($t.throughput * $t.meanResTime / 1000, 2)
        cpu_back_prom_pct = if ($back) { $back.avg } else { '' }
        cpu_back_max_pct  = if ($back) { $back.max } else { '' }
        cpu_pg_prom_pct   = if ($pg) { $pg.avg } else { '' }
        cpu_pg_max_pct    = if ($pg) { $pg.max } else { '' }
    }
}

$sorted = $rows | Sort-Object escenario, vus
$sorted | Format-Table -AutoSize
$sorted | Export-Csv -Path (Join-Path $root 'resumen.csv') -NoTypeInformation -Encoding UTF8
Write-Host "resumen.csv generado en $root"

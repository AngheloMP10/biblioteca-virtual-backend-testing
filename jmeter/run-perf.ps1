# Ejecuta los escenarios de JMeter en modo consola (sin GUI) para cada nivel de VUs.
# Requisitos: variable de entorno JMETER_HOME (carpeta apache-jmeter-5.6.3), contenedores
# biblioteca-backend y biblioteca-postgres levantados, y seed-perf.sql ya aplicado.
# Ejemplos:
#   .\run-perf.ps1                                   (get y post, 1/10/25/50/100 VUs)
#   .\run-perf.ps1 -Scenarios get -Levels 50,100
#   .\run-perf.ps1 -Scenarios get -Levels 100 -Think 0   (estres: sin tiempo de pensamiento)
param(
    [string[]]$Scenarios = @('get', 'post'),
    [int[]]$Levels = @(1, 10, 25, 50, 100),
    [int]$Loops = 30,
    [int]$Think = 1000,
    [int]$Users = 500,
    [int]$CooldownSec = 30,
    [string]$ApiHost = 'localhost',
    [int]$ApiPort = 8080,
    [string]$Container = 'biblioteca-postgres',
    [string]$DbName = 'biblioteca_db',
    [string]$DbUser = 'postgres',
    [string]$ResultsDir = "$PSScriptRoot\results",
    [switch]$SkipWarmup
)

$ErrorActionPreference = 'Stop'
if (-not $env:JMETER_HOME) { throw 'Define JMETER_HOME (carpeta apache-jmeter-5.6.3)' }
$jmeter = Join-Path $env:JMETER_HOME 'bin\jmeter.bat'
$baseUrl = "http://${ApiHost}:${ApiPort}"
$tokensCsv = "$PSScriptRoot\tokens.csv"
$solicitudesCsv = "$PSScriptRoot\solicitudes.csv"

function Invoke-Psql([string]$sql) {
    docker exec $Container psql -U $DbUser -d $DbName -t -A -c $sql
}

function Get-Ramp([int]$vus) {
    $map = @{ 1 = 1; 10 = 5; 25 = 10; 50 = 20; 100 = 30 }
    if ($map.ContainsKey($vus)) { return $map[$vus] }
    return [int][math]::Min(30, [math]::Ceiling($vus * 0.3))
}

# Paginas disponibles del catalogo (size=8) para variar la pagina en el GET
$totalLibros = [int](Invoke-Psql 'SELECT count(*) FROM libros' | Select-Object -First 1)
$maxPage = [int][math]::Max(0, [math]::Ceiling($totalLibros / 8.0) - 1)
Write-Host "Libros en BD: $totalLibros  (maxPage = $maxPage)"

# Tokens frescos (login de los usuarios perf; no tienen 2FA)
& "$PSScriptRoot\prepare-data.ps1" -Tokens -Users $Users -BaseUrl $baseUrl

if (-not $SkipWarmup) {
    Write-Host '>> Calentamiento (no se mide): la JVM y el pool de conexiones llegan en frio'
    & $jmeter -n -t "$PSScriptRoot\catalogo-get.jmx" '-Jvus=5' '-Jramp=2' '-Jloops=10' '-Jthink=200' `
        "-JmaxPage=$maxPage" "-Jhost=$ApiHost" "-Jport=$ApiPort" "-Jtokens=$tokensCsv" | Out-Null
    Start-Sleep -Seconds 10
}

foreach ($scenario in $Scenarios) {
    foreach ($vus in $Levels) {
        $ramp = Get-Ramp $vus
        $outDir = Join-Path $ResultsDir "$scenario\vus-$vus"
        if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
        New-Item -ItemType Directory -Path $outDir | Out-Null

        $common = @("-Jvus=$vus", "-Jramp=$ramp", "-Jthink=$Think", "-Jhost=$ApiHost", "-Jport=$ApiPort")

        if ($scenario -eq 'get') {
            $plan = "$PSScriptRoot\catalogo-get.jmx"
            $props = $common + @("-Jloops=$Loops", "-JmaxPage=$maxPage", "-Jtokens=$tokensCsv")
        }
        else {
            # Cada nivel parte sin prestamos de prueba y con filas validas (max. 3 por usuario)
            Invoke-Psql "DELETE FROM prestamos WHERE usuario_id IN (SELECT id FROM usuarios WHERE username LIKE 'perf%')" | Out-Null
            $lines = [int][math]::Min($vus * $Loops, $Users * 3)
            & "$PSScriptRoot\prepare-data.ps1" -Solicitudes -Lines $lines -Users $Users -Container $Container -DbName $DbName -DbUser $DbUser
            $plan = "$PSScriptRoot\solicitud-post.jmx"
            $props = $common + @("-Jsolicitudes=$solicitudesCsv")
        }

        Write-Host ">> $scenario | $vus VUs | ramp-up ${ramp}s | think ${Think}ms"
        # Muestreo de CPU/memoria de los contenedores durante la corrida (backend vs PostgreSQL)
        $statsJob = Start-Job -ScriptBlock {
            param($file, $names)
            while ($true) {
                $ts = Get-Date -Format 'HH:mm:ss'
                docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' @names |
                    ForEach-Object { "$ts,$_" } | Add-Content -Path $file
            }
        } -ArgumentList "$outDir\docker-stats.csv", @('biblioteca-backend', $Container)

        $jmArgs = @('-n', '-t', $plan) + $props + @('-l', "$outDir\resultados.jtl", '-e', '-o', "$outDir\report")
        & $jmeter @jmArgs
        $jmExit = $LASTEXITCODE

        Stop-Job $statsJob
        Remove-Job $statsJob -Force
        if ($jmExit -ne 0) { Write-Warning "JMeter termino con codigo $jmExit" }

        Write-Host "   enfriamiento ${CooldownSec}s"
        Start-Sleep -Seconds $CooldownSec
    }
}

if ($Scenarios -contains 'post') {
    Invoke-Psql "DELETE FROM prestamos WHERE usuario_id IN (SELECT id FROM usuarios WHERE username LIKE 'perf%')" | Out-Null
}

& "$PSScriptRoot\resumen.ps1" -ResultsDir $ResultsDir

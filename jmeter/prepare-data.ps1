# Prepara los CSV que lee JMeter. (Solo ASCII para evitar problemas de codificacion en PowerShell 5.1)
#   -Tokens       : hace login de perf001..perfNNN y guarda tokens.csv (username,token)
#   -Solicitudes  : genera solicitudes.csv (token,libroId) con -Lines filas, maximo 3 por usuario
param(
    [switch]$Tokens,
    [switch]$Solicitudes,
    [int]$Users = 500,
    [int]$Lines = 1500,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Password = 'Perf123@',
    [string]$Container = 'biblioteca-postgres',
    [string]$DbName = 'biblioteca_db',
    [string]$DbUser = 'postgres'
)

$ErrorActionPreference = 'Stop'
$tokensFile = Join-Path $PSScriptRoot 'tokens.csv'
$solicitudesFile = Join-Path $PSScriptRoot 'solicitudes.csv'

if ($Tokens) {
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add('username,token')
    for ($i = 1; $i -le $Users; $i++) {
        $u = 'perf{0:D3}' -f $i
        $body = @{ username = $u; password = $Password } | ConvertTo-Json
        $resp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType 'application/json' -Body $body
        if (-not $resp.token) { throw "Login sin token para $u (el usuario debe existir y no tener 2FA)" }
        $rows.Add("$u,$($resp.token)")
    }
    Set-Content -Path $tokensFile -Value $rows -Encoding ascii
    Write-Host "tokens.csv listo: $Users usuarios"
}

if ($Solicitudes) {
    # Solo libros con stock > 0: el servicio rechaza (500) si el stock es 0
    $ids = docker exec $Container psql -U $DbUser -d $DbName -t -A -c "SELECT id FROM libros WHERE disponible = true" |
        Where-Object { $_ -match '^\d+$' }
    if (-not $ids) { throw 'No se pudieron leer ids de libros con stock' }

    $tokenRows = Import-Csv $tokensFile
    $rows = New-Object System.Collections.Generic.List[string]
    $rows.Add('token,libroId')
    $count = 0

    # 3 rondas: en cada ronda cada usuario hace 1 solicitud (limite de negocio = 3 activos),
    # asi ninguna solicitud del test provoca un error de negocio.
    foreach ($round in 1..3) {
        foreach ($t in $tokenRows) {
            if ($count -ge $Lines) { break }
            $rows.Add("$($t.token),$(Get-Random -InputObject @($ids))")
            $count++
        }
    }
    Set-Content -Path $solicitudesFile -Value $rows -Encoding ascii
    Write-Host "solicitudes.csv listo: $count filas"
}

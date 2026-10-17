# Mini-runner para verificar una coleccion sin Postman ni Newman.
# Ejecuta los requests en orden, resuelve {{variables}}, emula los pre-request
# conocidos (doc, phone, yesterday, esperas) y verifica:
#   - el codigo HTTP de pm.response.to.have.status(N)
#   - el texto de los tests 'contiene: ...'
#   - guarda las variables de pm.collectionVariables.set('x', pm.response.json().y)
# Uso: powershell -File tools/mini-runner.ps1 collections/bank-part-3.postman_collection.json environments/bank-local.postman_environment.json
param([string]$Collection, [string]$Environment)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$vars = @{}
(Get-Content -Raw $Environment | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
$col = Get-Content -Raw $Collection | ConvertFrom-Json
$col.variable | ForEach-Object { if (-not $vars.ContainsKey($_.key)) { $vars[$_.key] = $_.value } }
$pass = 0; $fail = 0; $failures = @()

function Resolve([string]$text) {
    if ($null -eq $text) { return $null }
    return [regex]::Replace($text, '\{\{(\w+)\}\}', { param($m) [string]$vars[$m.Groups[1].Value] })
}
function Field($json, [string]$path) {
    $value = $json
    foreach ($part in $path.Split('.')) { if ($null -eq $value) { return $null }; $value = $value.$part }
    return $value
}

foreach ($folder in $col.item) {
    Write-Output "=== $($folder.name)"
    foreach ($item in $folder.item) {
        $pre = @(); $tests = @()
        foreach ($e in @($item.event)) {
            if ($e.listen -eq 'prerequest') { $pre += @($e.script.exec) } else { $tests += @($e.script.exec) }
        }
        foreach ($line in $pre) {
            if ($line -match "set\('doc'") { $vars['doc'] = [string](Get-Random -Minimum 10000000 -Maximum 99999999) }
            if ($line -match "set\('phone'") { $vars['phone'] = '9' + [string](Get-Random -Minimum 10000000 -Maximum 99999999) }
            if ($line -match "set\('yesterday'") { $vars['yesterday'] = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd') }
            if ($line -match 'setTimeout') { Start-Sleep -Seconds 3 }
        }
        $req = $item.request
        $message = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($req.method), (Resolve $req.url.raw))
        $token = $null
        if ($req.auth) { if ($req.auth.type -eq 'bearer') { $token = Resolve $req.auth.bearer[0].value } }
        else { $token = $vars['token'] }
        if ($token) { $message.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token) }
        if ($req.body) { $message.Content = New-Object System.Net.Http.StringContent((Resolve $req.body.raw), [Text.Encoding]::UTF8, 'application/json') }
        $response = $client.SendAsync($message).Result
        $code = [int]$response.StatusCode
        $text = $response.Content.ReadAsStringAsync().Result
        $json = $null; try { $json = $text | ConvertFrom-Json } catch { }
        $ok = $true; $why = @()
        foreach ($line in $tests) {
            if ($line -match 'have\.status\((\d+)\)' -and $code -ne [int]$Matches[1]) { $ok = $false; $why += "esperaba HTTP $($Matches[1]), obtuvo $code" }
            if ($line -match "contiene: (.+)', \(\)" -and -not $text.Contains($Matches[1])) { $ok = $false; $why += "no contiene $($Matches[1])" }
            if ($line -match "collectionVariables\.set\('(\w+)', pm\.response\.json\(\)\.([\w.]+)\)" -and $json) { $vars[$Matches[1]] = [string](Field $json $Matches[2]) }
            if ($line -match "pm\.test\('([\w.]+) = ([^{][^']*)', \(\) => pm\.expect\(String" -and $json) {
                $actual = [string](Field $json $Matches[1]); if ($actual -ne $Matches[2]) { $ok = $false; $why += "$($Matches[1]): esperaba $($Matches[2]), obtuvo $actual" }
            }
        }
        if ($ok) { $pass++; Write-Output "  OK   $($item.name) ($code)" }
        else { $fail++; $failures += "$($folder.name) / $($item.name): $($why -join '; ')"; Write-Output "  FAIL $($item.name): $($why -join '; ') -> $($text.Substring(0, [Math]::Min(200, $text.Length)))" }
    }
}
Write-Output ""
Write-Output "RESULTADO COLECCION: $pass OK, $fail FAIL"
$failures | ForEach-Object { Write-Output "  - $_" }

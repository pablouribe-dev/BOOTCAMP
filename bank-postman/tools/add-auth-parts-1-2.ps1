# Agrega JWT a la coleccion de las Partes I y II (Parte III: el gateway exige token):
# auth Bearer {{token}} a nivel de coleccion y un login de ADMIN al principio.
# Idempotente: si ya tiene la carpeta de login, no la vuelve a agregar.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'collections\bank-parts-1-2.postman_collection.json'
$col = Get-Content -Raw $path | ConvertFrom-Json

if ($col.item[0].name -ne '00 - Login (Parte III)') {
    $login = [pscustomobject]@{
        name        = '00 - Login (Parte III)'
        description = 'Desde la Parte III el gateway exige JWT. Este login de ADMIN guarda el token que la coleccion envia como Bearer {{token}} (las pruebas hacen PUT y DELETE, que exigen ADMIN).'
        item        = @([pscustomobject]@{
            name    = 'Login ADMIN'
            event   = @([pscustomobject]@{ listen = 'test'; script = [pscustomobject]@{ type = 'text/javascript'; exec = @(
                "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                "pm.collectionVariables.set('token', pm.response.json().accessToken);") } })
            request = [pscustomobject]@{
                method = 'POST'
                auth   = [pscustomobject]@{ type = 'noauth' }
                header = @([pscustomobject]@{ key = 'Content-Type'; value = 'application/json' })
                body   = [pscustomobject]@{ mode = 'raw'; raw = '{"username":"{{adminUsername}}","password":"{{adminPassword}}"}' }
                url    = [pscustomobject]@{ raw = '{{customerUrl}}/auth/login'; host = @('{{customerUrl}}'); path = @('auth', 'login') }
            }
        })
    }
    $col.item = @($login) + @($col.item)
}
$col | Add-Member -NotePropertyName auth -NotePropertyValue ([pscustomobject]@{
    type = 'bearer'; bearer = @([pscustomobject]@{ key = 'token'; value = '{{token}}'; type = 'string' }) }) -Force
foreach ($key in 'token', 'adminUsername', 'adminPassword') {
    if (-not ($col.variable | Where-Object { $_.key -eq $key })) {
        $default = @{ token = ''; adminUsername = 'admin'; adminPassword = 'Admin123!' }[$key]
        $col.variable = @($col.variable) + @([pscustomobject]@{ key = $key; value = $default })
    }
}
$col.info.description = $col.info.description + "`n`nParte III: el gateway exige JWT. La carpeta 00 hace login como ADMIN (adminUsername/adminPassword, tambien en el environment bank-local) y la coleccion usa Bearer {{token}}."
$json = $col | ConvertTo-Json -Depth 50
[System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Coleccion actualizada: $(@($col.item).Count) carpetas"

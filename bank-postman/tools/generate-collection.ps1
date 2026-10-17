# Genera collections/bank-part-3.postman_collection.json (Parte III).
# Uso (PowerShell, desde bank-postman):  powershell -File tools/generate-collection.ps1
# La coleccion usa Bearer {{token}} a nivel de coleccion; el login guarda el token.

$ErrorActionPreference = 'Stop'

function T-Status($code) { "pm.test('HTTP $code', () => pm.response.to.have.status($code));" }
function T-Save($var, $path) { "pm.collectionVariables.set('$var', pm.response.json().$path);" }
function T-Eq($path, $value) { "pm.test('$path = $value', () => pm.expect(String(pm.response.json().$path)).to.eql('$value'));" }
function T-EqVar($path, $var) { "pm.test('$path = {{$var}}', () => pm.expect(pm.response.json().$path).to.eql(pm.collectionVariables.get('$var')));" }
function T-Num($path, $value) { "pm.test('$path = $value', () => pm.expect(Number(pm.response.json().$path)).to.eql($value));" }
function T-Has($text) { "pm.test('contiene: $text', () => pm.expect(pm.response.text()).to.include('$text'));" }
$Wait = "setTimeout(() => {}, 3000); // espera a que el evento de Kafka se propague"
$RandDoc = "pm.collectionVariables.set('doc', String(Math.floor(10000000 + Math.random() * 89999999)));"
$RandPhone = "pm.collectionVariables.set('phone', '9' + String(Math.floor(10000000 + Math.random() * 89999999)));"
$Yesterday = "pm.collectionVariables.set('yesterday', new Date(Date.now() - 86400000).toISOString().substring(0, 10));"

function Req {
    param([string]$Name, [string]$Method, [string]$Path, [string]$Body = $null, [string[]]$Tests = @(),
          [string[]]$Pre = @(), [string]$Auth = 'collection', [string]$Description = '')
    $request = [ordered]@{
        method = $Method
        header = @(@{ key = 'Content-Type'; value = 'application/json' })
        url    = [ordered]@{ raw = "{{baseUrl}}$Path"; host = @('{{baseUrl}}'); path = @($Path.TrimStart('/').Split('?')[0].Split('/')) }
    }
    if ($Path.Contains('?')) {
        $request.url.query = @($Path.Split('?')[1].Split('&') | ForEach-Object { $kv = $_.Split('='); @{ key = $kv[0]; value = $kv[1] } })
    }
    if ($Description) { $request.description = $Description }
    if ($Body) { $request.body = [ordered]@{ mode = 'raw'; raw = $Body; options = @{ raw = @{ language = 'json' } } } }
    if ($Auth -eq 'none') { $request.auth = @{ type = 'noauth' } }
    elseif ($Auth -ne 'collection') {
        $request.auth = [ordered]@{ type = 'bearer'; bearer = @(@{ key = 'token'; value = "{{$Auth}}"; type = 'string' }) }
    }
    $events = @()
    if ($Pre.Count -gt 0) { $events += [ordered]@{ listen = 'prerequest'; script = @{ type = 'text/javascript'; exec = $Pre } } }
    if ($Tests.Count -gt 0) { $events += [ordered]@{ listen = 'test'; script = @{ type = 'text/javascript'; exec = $Tests } } }
    [ordered]@{ name = $Name; event = $events; request = $request }
}
function Folder([string]$Name, [string]$Description, [object[]]$Items) {
    [ordered]@{ name = $Name; description = $Description; item = $Items }
}

$login = Folder '00 - Login' 'Obtiene el token de ADMIN (queda como token de la coleccion) y crea un USER para probar los roles.' @(
    (Req 'Login ADMIN' 'POST' '/auth/login' '{"username":"{{adminUsername}}","password":"{{adminPassword}}"}' -Auth 'none' -Tests @(
        (T-Status 200), (T-Eq 'tokenType' 'Bearer'),
        "pm.collectionVariables.set('token', pm.response.json().accessToken);",
        "pm.collectionVariables.set('adminToken', pm.response.json().accessToken);")),
    (Req 'Registro publico (rol USER)' 'POST' '/auth/register' '{"username":"user{{doc}}","password":"Secret123","phoneNumber":"{{phone}}"}' -Auth 'none' -Pre @($RandDoc, $RandPhone) -Tests @(
        (T-Status 201), (T-Has '"roles":["USER"]'),
        "pm.test('sin passwordHash', () => pm.expect(pm.response.json().passwordHash).to.be.undefined);",
        "pm.collectionVariables.set('username', pm.response.json().username);")),
    (Req 'Login USER' 'POST' '/auth/login' '{"username":"{{username}}","password":"Secret123"}' -Auth 'none' -Tests @(
        (T-Status 200), "pm.collectionVariables.set('userToken', pm.response.json().accessToken);"))
)

$auth = Folder '01 - auth-service' 'Usuarios y JWT. /auth/users/** exige ADMIN.' @(
    (Req '[401] login invalido (mensaje generico)' 'POST' '/auth/login' '{"username":"{{adminUsername}}","password":"incorrecta"}' -Auth 'none' -Tests @((T-Status 401), (T-Has 'Invalid username or password'))),
    (Req 'Crear usuario ADMIN (ADMIN)' 'POST' '/auth/users' '{"username":"ops{{doc}}","password":"Secret123","roles":["ADMIN"]}' -Pre @($RandDoc) -Tests @((T-Status 201), (T-Save 'userId' 'id'))),
    (Req 'Listar usuarios (ADMIN)' 'GET' '/auth/users' -Tests @((T-Status 200))),
    (Req 'Buscar usuario (ADMIN)' 'GET' '/auth/users/{{userId}}' -Tests @((T-Status 200))),
    (Req 'Actualizar usuario (ADMIN)' 'PUT' '/auth/users/{{userId}}' '{"roles":["USER"],"phoneNumber":"999888777"}' -Tests @((T-Status 200), (T-Has '"roles":["USER"]'))),
    (Req '[403] USER no administra usuarios' 'GET' '/auth/users' -Auth 'userToken' -Tests @((T-Status 403))),
    (Req 'Eliminar usuario (ADMIN)' 'DELETE' '/auth/users/{{userId}}' -Tests @((T-Status 204)))
)

$customer = Folder '02 - customer-service' 'Publica CUSTOMER_UPSERTED/DELETED y cachea en Redis (customer:id / customer:doc).' @(
    (Req 'Crear cliente PERSONAL' 'POST' '/customers' '{"type":"PERSONAL","documentType":"DNI","documentNumber":"{{doc}}","name":"Cliente Parte III"}' -Pre @($RandDoc) -Tests @((T-Status 201), (T-Save 'customerId' 'id'), "pm.collectionVariables.set('customerDoc', pm.response.json().documentNumber);")),
    (Req 'Buscar por documento (cache Redis)' 'GET' '/customers?documentNumber={{customerDoc}}' -Tests @((T-Status 200), "pm.test('encontrado', () => pm.expect(pm.response.json()[0].id).to.eql(pm.collectionVariables.get('customerId')));")),
    (Req 'Buscar por id (cache Redis)' 'GET' '/customers/{{customerId}}' -Tests @((T-Status 200))),
    (Req 'Republicar snapshots (ADMIN)' 'POST' '/admin/customers/events/republish' -Tests @((T-Status 200), (T-Eq 'eventType' 'CUSTOMER_UPSERTED'))),
    (Req '[403] republicar con USER' 'POST' '/admin/customers/events/republish' -Auth 'userToken' -Tests @((T-Status 403)))
)

$account = Folder '03 - account-service' 'Publica ACCOUNT_UPSERTED/DELETED, aplica las operaciones pedidas por Kafka y la regla de deuda vencida.' @(
    (Req 'Cuenta de ahorro (50)' 'POST' '/accounts' '{"accountType":"SAVINGS","customerId":"{{customerId}}","openingAmount":50}' -Tests @((T-Status 201), (T-Save 'savingsId' 'id'))),
    (Req 'Cuenta corriente (500)' 'POST' '/accounts' '{"accountType":"CURRENT","customerId":"{{customerId}}","openingAmount":500}' -Tests @((T-Status 201), (T-Save 'currentId' 'id'))),
    (Req 'Republicar snapshots (ADMIN)' 'POST' '/admin/accounts/events/republish' -Tests @((T-Status 200), (T-Eq 'eventType' 'ACCOUNT_UPSERTED')))
)

$debit = Folder '04 - debit-card-service' 'Tarjeta de debito con copias locales (sin REST) y pagos con fallback de cuentas.' @(
    (Req 'Crear tarjeta (ahorro principal + corriente)' 'POST' '/debit-cards' '{"customerId":"{{customerId}}","accountIds":["{{savingsId}}","{{currentId}}"],"mainAccountId":"{{savingsId}}"}' -Pre @($Wait) -Tests @((T-Status 201), (T-Save 'debitCardId' 'id'), (T-Save 'debitCardNumber' 'cardNumber'), (T-Eq 'status' 'ACTIVE'))),
    (Req 'Listar tarjetas del cliente' 'GET' '/debit-cards?customerId={{customerId}}' -Tests @((T-Status 200))),
    (Req 'Buscar tarjeta' 'GET' '/debit-cards/{{debitCardId}}' -Tests @((T-Status 200))),
    (Req 'Pagar 80 (202 PENDING)' 'POST' '/debit-cards/{{debitCardId}}/payments' '{"amount":80,"description":"supermercado"}' -Tests @((T-Status 202), (T-Eq 'status' 'PENDING'), (T-Save 'debitPaymentId' 'paymentId'))),
    (Req 'Estado del pago: COMPLETED en la corriente (fallback)' 'GET' '/debit-cards/{{debitCardId}}/payments/{{debitPaymentId}}' -Pre @($Wait) -Tests @((T-Status 200), (T-Eq 'status' 'COMPLETED'), (T-EqVar 'chargedAccountId' 'currentId'))),
    (Req 'Saldo de la cuenta principal (sin tocar)' 'GET' '/accounts/{{savingsId}}/balance' -Tests @((T-Status 200), (T-Num 'balance' 50))),
    (Req 'Saldo de la corriente (500 - 80)' 'GET' '/accounts/{{currentId}}/balance' -Tests @((T-Status 200), (T-Num 'balance' 420))),
    (Req 'Historial de pagos' 'GET' '/debit-cards/{{debitCardId}}/payments' -Tests @((T-Status 200))),
    (Req 'Cambiar cuenta principal' 'PUT' '/debit-cards/{{debitCardId}}/main-account' '{"accountId":"{{currentId}}"}' -Tests @((T-Status 200), (T-EqVar 'mainAccountId' 'currentId')))
)

$transaction = Folder '05 - transaction-service' 'Movimientos desde eventos y pago de terceros.' @(
    (Req 'Ultimos movimientos de la tarjeta de debito' 'GET' '/reports/last-movements?productType=DEBIT_CARD&productId={{debitCardId}}' -Tests @((T-Status 200), (T-Has '"transactionType":"DEBIT_PAYMENT"'), (T-Has '"origin":"DEBIT_CARD"'))),
    (Req 'Credito del cliente' 'POST' '/credits' '{"creditType":"PERSONAL","customerId":"{{customerId}}","grantedAmount":1000,"interestRate":10}' -Tests @((T-Status 201), (T-Save 'creditId' 'id'))),
    (Req 'Pagador tercero' 'POST' '/customers' '{"type":"PERSONAL","documentType":"DNI","documentNumber":"{{doc}}","name":"Pagador Tercero"}' -Pre @($RandDoc) -Tests @((T-Status 201), (T-Save 'payerId' 'id'))),
    (Req 'Pago de terceros (thirdParty=true)' 'POST' '/transactions/payment' '{"productType":"CREDIT","productId":"{{creditId}}","amount":100,"payerCustomerId":"{{payerId}}"}' -Tests @((T-Status 201), (T-Eq 'thirdParty' 'true'), (T-EqVar 'payerCustomerId' 'payerId'), (T-EqVar 'customerId' 'customerId'))),
    (Req '[404] pagador inexistente' 'POST' '/transactions/payment' '{"productType":"CREDIT","productId":"{{creditId}}","amount":1,"payerCustomerId":"000000000000000000000000"}' -Tests @((T-Status 404)))
)

$credit = Folder '06 - credit-service y credit-card-service' 'Vencimientos, deteccion diaria (se fuerza con /admin/.../scan) y DEBT_OVERDUE_DETECTED/CLEARED.' @(
    (Req 'Fijar cuota vencida en el credito (PUT)' 'PUT' '/credits/{{creditId}}' '{"creditType":"PERSONAL","customerId":"{{customerId}}","grantedAmount":1000,"interestRate":10,"dueDate":"{{yesterday}}","dueAmount":150}' -Pre @($Yesterday) -Tests @((T-Status 200), (T-Num 'dueAmount' 150))),
    (Req 'Detectar deudas vencidas de creditos (ADMIN)' 'POST' '/admin/credits/overdue-debts/scan' -Tests @((T-Status 200), (T-Eq 'productType' 'CREDIT'))),
    (Req 'Detectar deudas vencidas de tarjetas (ADMIN)' 'POST' '/admin/credit-cards/overdue-debts/scan' -Tests @((T-Status 200), (T-Eq 'productType' 'CREDIT_CARD'))),
    (Req '[409] con deuda vencida no puede sacar tarjeta de credito' 'POST' '/credit-cards' '{"cardType":"PERSONAL","customerId":"{{customerId}}","creditLimit":1000}' -Pre @($Wait) -Tests @((T-Status 409), (T-Has 'overdue debt'))),
    (Req 'Pagar la cuota (libera al cliente)' 'POST' '/transactions/payment' '{"productType":"CREDIT","productId":"{{creditId}}","amount":150}' -Tests @((T-Status 201))),
    (Req 'Credito ya no vencido' 'GET' '/credits/{{creditId}}' -Tests @((T-Status 200), (T-Num 'dueAmount' 0), (T-Eq 'overdueNotified' 'false')))
)

$yanki = Folder '07 - yanki-service' 'Monedero movil: sin tarjeta usa su saldo; con tarjeta, la cuenta principal.' @(
    (Req 'Monedero A (sin tarjeta)' 'POST' '/yanki/wallets' '{"documentType":"DNI","documentNumber":"{{doc}}","phoneNumber":"{{phone}}","imei":"356938035643809","email":"a@yanki.pe"}' -Pre @($RandDoc, $RandPhone) -Tests @((T-Status 201), (T-Save 'walletA' 'id'), (T-Save 'phoneA' 'phoneNumber'), (T-Num 'balance' 0))),
    (Req 'Monedero B' 'POST' '/yanki/wallets' '{"documentType":"CEX","documentNumber":"{{doc}}","phoneNumber":"{{phone}}","imei":"356938035643810","email":"b@yanki.pe"}' -Pre @($RandDoc, $RandPhone) -Tests @((T-Status 201), (T-Save 'walletB' 'id'), (T-Save 'phoneB' 'phoneNumber'))),
    (Req 'Asociar B a la tarjeta de debito' 'PUT' '/yanki/wallets/{{walletB}}/debit-card' '{"cardNumber":"{{debitCardNumber}}"}' -Tests @((T-Status 200), (T-EqVar 'debitCardNumber' 'debitCardNumber'))),
    (Req 'B paga 50 a A (202)' 'POST' '/yanki/payments' '{"fromPhoneNumber":"{{phoneB}}","toPhoneNumber":"{{phoneA}}","amount":50,"description":"almuerzo"}' -Tests @((T-Status 202), (T-Save 'yankiPaymentId' 'paymentId'))),
    (Req 'Estado del pago: COMPLETED' 'GET' '/yanki/payments/{{yankiPaymentId}}' -Pre @($Wait) -Tests @((T-Status 200), (T-Eq 'status' 'COMPLETED'))),
    (Req 'Saldo propio de A = 50' 'GET' '/yanki/wallets/{{walletA}}' -Tests @((T-Status 200), (T-Num 'balance' 50))),
    (Req '[409] A no tiene saldo suficiente' 'POST' '/yanki/payments' '{"fromPhoneNumber":"{{phoneA}}","toPhoneNumber":"{{phoneB}}","amount":1000}' -Tests @((T-Status 409), (T-Has 'Insufficient wallet balance'))),
    (Req 'Historial de A' 'GET' '/yanki/payments?phoneNumber={{phoneA}}' -Tests @((T-Status 200))),
    (Req 'Quitar la tarjeta de B' 'DELETE' '/yanki/wallets/{{walletB}}/debit-card' -Tests @((T-Status 200)))
)

$e2e = Folder '99 - Flujo de punta a punta (reglas violadas)' 'Caso feliz y reglas violadas: deuda vencida, debito con fallback, Yanki con y sin tarjeta, sin token y rol insuficiente.' @(
    (Req '[401] sin token' 'GET' '/customers' -Auth 'none' -Tests @((T-Status 401))),
    (Req '[403] USER intenta eliminar' 'DELETE' '/customers/{{customerId}}' -Auth 'userToken' -Tests @((T-Status 403), (T-Has 'Insufficient role'))),
    (Req '[403] endpoint interno bloqueado' 'POST' '/accounts/{{savingsId}}/balance-operations' '{"operation":"DEPOSIT","amount":1}' -Tests @((T-Status 403))),
    (Req 'Moroso: cliente' 'POST' '/customers' '{"type":"PERSONAL","documentType":"DNI","documentNumber":"{{doc}}","name":"Moroso"}' -Pre @($RandDoc, $Yesterday) -Tests @((T-Status 201), (T-Save 'debtorId' 'id'))),
    (Req 'Moroso: credito' 'POST' '/credits' '{"creditType":"PERSONAL","customerId":"{{debtorId}}","grantedAmount":500,"interestRate":10}' -Tests @((T-Status 201), (T-Save 'debtorCreditId' 'id'))),
    (Req 'Moroso: cuota vencida' 'PUT' '/credits/{{debtorCreditId}}' '{"creditType":"PERSONAL","customerId":"{{debtorId}}","grantedAmount":500,"interestRate":10,"dueDate":"{{yesterday}}","dueAmount":80}' -Tests @((T-Status 200))),
    (Req 'Moroso: deteccion (ADMIN)' 'POST' '/admin/credits/overdue-debts/scan' -Tests @((T-Status 200))),
    (Req '[409] moroso no puede abrir cuenta' 'POST' '/accounts' '{"accountType":"SAVINGS","customerId":"{{debtorId}}","openingAmount":10}' -Pre @($Wait) -Tests @((T-Status 409), (T-Has 'overdue debt'))),
    (Req 'Pago con debito de 2000: ninguna cuenta alcanza' 'POST' '/debit-cards/{{debitCardId}}/payments' '{"amount":2000}' -Tests @((T-Status 202), (T-Save 'failedPaymentId' 'paymentId'))),
    (Req 'Pago fallido (FAILED, INSUFFICIENT_FUNDS)' 'GET' '/debit-cards/{{debitCardId}}/payments/{{failedPaymentId}}' -Pre @($Wait) -Tests @((T-Status 200), (T-Eq 'status' 'FAILED'), (T-Eq 'failureReason' 'INSUFFICIENT_FUNDS'))),
    (Req 'Yanki sin tarjeta: A paga 20 a B (al instante)' 'POST' '/yanki/payments' '{"fromPhoneNumber":"{{phoneA}}","toPhoneNumber":"{{phoneB}}","amount":20}' -Tests @((T-Status 202), (T-Eq 'status' 'COMPLETED'))),
    (Req '[409] Yanki al mismo celular' 'POST' '/yanki/payments' '{"fromPhoneNumber":"{{phoneA}}","toPhoneNumber":"{{phoneA}}","amount":1}' -Tests @((T-Status 409)))
)

$collection = [ordered]@{
    info     = [ordered]@{
        name        = 'Bank - Parte III (JWT, Kafka, Redis, debito y Yanki)'
        description = "Parte III del sistema bancario, todo a traves del API Gateway con JWT.`n`nUsar el environment bank-local (baseUrl, adminUsername, adminPassword). El request '00 - Login / Login ADMIN' guarda el token y la coleccion lo envia como Bearer {{token}}.`n`nEjecutar EN ORDEN con el Collection Runner: cada carpeta usa ids guardados por las anteriores. Las esperas de 3 s dejan que los eventos de Kafka se propaguen."
        schema      = 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
    }
    auth     = [ordered]@{ type = 'bearer'; bearer = @(@{ key = 'token'; value = '{{token}}'; type = 'string' }) }
    variable = @('token', 'adminToken', 'userToken', 'username', 'doc', 'phone', 'yesterday', 'userId', 'customerId', 'customerDoc',
        'savingsId', 'currentId', 'debitCardId', 'debitCardNumber', 'debitPaymentId', 'creditId', 'payerId', 'walletA', 'walletB',
        'phoneA', 'phoneB', 'yankiPaymentId', 'debtorId', 'debtorCreditId', 'failedPaymentId' | ForEach-Object { @{ key = $_; value = '' } })
    item     = @($login, $auth, $customer, $account, $debit, $transaction, $credit, $yanki, $e2e)
}

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'collections\bank-part-3.postman_collection.json'
$json = $collection | ConvertTo-Json -Depth 30
[System.IO.File]::WriteAllText($out, $json, (New-Object System.Text.UTF8Encoding($false)))
$count = ($collection.item | ForEach-Object { $_.item.Count } | Measure-Object -Sum).Sum
Write-Output "Coleccion generada: $out ($count requests)"

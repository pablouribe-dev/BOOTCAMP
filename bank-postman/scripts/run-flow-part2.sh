#!/usr/bin/env bash
# Prueba de punta a punta de la Parte II, todo a traves del API Gateway.
# Uso: bash run-flow-part2.sh   (con Mongo, Config Server, Eureka, los 5 servicios y el Gateway arriba)
GW=${GATEWAY:-http://localhost:8080}
PASS=0; FAIL=0; FAILS=()

# Parte III: el gateway exige JWT. Se usa un token de ADMIN (las pruebas hacen PUT y DELETE).
LOGIN_URL=${GATEWAY:-http://localhost:8080}/auth/login
TOKEN=$(curl -s -X POST "$LOGIN_URL" -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USERNAME:-admin}\",\"password\":\"${ADMIN_PASSWORD:-Admin123!}\"}" \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
[ -z "$TOKEN" ] && echo "AVISO: no se pudo obtener el token de ADMIN en $LOGIN_URL"

req() { # metodo url [body]
  local out
  if [ -n "$3" ]; then out=$(curl -s -w $'\n%{http_code}' -X "$1" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "$3" "$2")
  else out=$(curl -s -w $'\n%{http_code}' -X "$1" -H "Authorization: Bearer $TOKEN" "$2"); fi
  CODE="${out##*$'\n'}"; BODY="${out%$'\n'*}"
}
str() { echo "$BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed 's/.*":"//; s/"$//'; }
num() { echo "$BODY" | grep -o "\"$1\":[-0-9.]*" | head -1 | sed 's/.*://'; }
raw() { echo "$BODY" | grep -o "\"$1\":[a-z]*" | head -1 | sed 's/.*://'; }
count() { echo "$BODY" | grep -o "\"$1\"" | wc -l | tr -d ' '; }
ok() { PASS=$((PASS+1)); printf '  \033[32mOK\033[0m   %s\n' "$1"; }
ko() { FAIL=$((FAIL+1)); FAILS+=("$CURRENT :: $1"); printf '  \033[31mFAIL\033[0m %s\n         -> HTTP %s %s\n' "$1" "$CODE" "${BODY:0:300}"; }
step() { CURRENT="$1"; echo "* $1"; }
expect() { [ "$CODE" = "$1" ] && ok "HTTP $1" || ko "esperaba HTTP $1"; }
has() { echo "$BODY" | grep -qF -- "$1" && ok "contiene: $1" || ko "deberia contener: $1"; }
eqnum() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a+0==b+0)}' && ok "$3 = $2" || ko "$3: esperaba $2, obtuvo '$1'"; }
eqstr() { [ "$1" = "$2" ] && ok "$3 = $2" || ko "$3: esperaba '$2', obtuvo '$1'"; }
doc() { echo $(( 10000000 + RANDOM * 2000 + RANDOM % 2000 )); }
ruc() { echo "20$(( 100000000 + RANDOM * 20000 + RANDOM ))"; }

TODAY=$(date +%F); MONTH=$(date +%Y-%m)
HOLDER='"holders":[{"documentType":"DNI","documentNumber":"45678912","fullName":"Ana Torres"}]'

echo "=== 06 - Perfiles de cliente"
step "[409] perfil VIP para cliente BUSINESS"
req POST "$GW/customers" "{\"type\":\"BUSINESS\",\"documentType\":\"RUC\",\"documentNumber\":\"$(ruc)\",\"name\":\"Empresa VIP\",\"profile\":\"VIP\"}"
expect 409; has "Profile VIP is not allowed for a BUSINESS customer"
step "[409] perfil PYME para cliente PERSONAL"
req POST "$GW/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"name\":\"Persona PYME\",\"profile\":\"PYME\"}"
expect 409; has "Profile PYME is not allowed for a PERSONAL customer"
step "Cliente PERSONAL VIP"
req POST "$GW/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"name\":\"Valeria Vip\",\"profile\":\"VIP\"}"
expect 201; eqstr "$(str profile)" VIP profile; VIP=$(str id)
step "Cliente BUSINESS PYME"
req POST "$GW/customers" "{\"type\":\"BUSINESS\",\"documentType\":\"RUC\",\"documentNumber\":\"$(ruc)\",\"name\":\"Pyme Andina SAC\",\"profile\":\"PYME\"}"
expect 201; eqstr "$(str profile)" PYME profile; PYME=$(str id)
step "Cliente PERSONAL STANDARD (para transferencias propias)"
req POST "$GW/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"name\":\"Sergio Std\"}"
expect 201; STD=$(str id)

echo "=== 07 - Cuentas: apertura, VIP, PYME"
step "[409] VIP sin tarjeta de credito"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$VIP\",\"openingAmount\":2000}"
expect 409; has "requires the customer to hold a credit card"
step "[409] PYME sin tarjeta de credito"
req POST "$GW/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$PYME\",\"openingAmount\":1000,$HOLDER}"
expect 409; has "requires the customer to hold a credit card"
step "Tarjeta para el cliente VIP"
req POST "$GW/credit-cards" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$VIP\",\"creditLimit\":5000}"; expect 201; VCARD=$(str id)
step "Tarjeta para el cliente PYME"
req POST "$GW/credit-cards" "{\"cardType\":\"BUSINESS\",\"customerId\":\"$PYME\",\"creditLimit\":8000}"; expect 201; PCARD=$(str id)
step "[409] VIP con monto de apertura bajo el minimo (500)"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$VIP\",\"openingAmount\":100}"
expect 409; has "opening amount must be at least 500"
step "[409] VIP pidiendo cuenta corriente"
req POST "$GW/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$VIP\",\"openingAmount\":2000}"
expect 409; has "VIP customer can only open SAVINGS accounts"
step "Cuenta SAVINGS VIP (apertura 2000)"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$VIP\",\"openingAmount\":2000}"
expect 201; eqstr "$(str profile)" VIP profile; eqnum "$(num minimumDailyAverage)" 1000 minimumDailyAverage
eqnum "$(num openingAmount)" 2000 openingAmount; eqnum "$(num balance)" 2000 balance
VSAV=$(str id); VSAVNUM=$(str accountNumber)
step "Cuenta CURRENT PYME (sin comision de mantenimiento)"
req POST "$GW/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$PYME\",\"openingAmount\":1000,\"maintenanceFee\":25,$HOLDER}"
expect 201; eqstr "$(str profile)" PYME profile; eqnum "$(num maintenanceFee)" 0 maintenanceFee
eqnum "$(num freeTransactionLimit)" 10 freeTransactionLimit; eqnum "$(num transactionFee)" 1.5 transactionFee
PCUR=$(str id)
step "Cuentas SAVINGS y CURRENT del cliente STANDARD"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$STD\",\"openingAmount\":300}"; expect 201; SSAV=$(str id)
eqstr "$(str profile)" STANDARD profile
req POST "$GW/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$STD\",\"initialBalance\":50}"; expect 201; SCUR=$(str id); SCURNUM=$(str accountNumber)
eqnum "$(num openingAmount)" 50 "openingAmount (alias initialBalance de la Parte I)"
step "Filtro GET /accounts?accountNumber="
req GET "$GW/accounts?accountNumber=$VSAVNUM"; expect 200; eqnum "$(count accountNumber)" 1 cantidad; has "\"id\":\"$VSAV\""
step "Promedio diario de la cuenta VIP ($MONTH)"
req GET "$GW/accounts/$VSAV/average-daily-balance?month=$MONTH"; expect 200
eqnum "$(num averageDailyBalance)" 2000 averageDailyBalance; eqstr "$(raw meetsMinimum)" true meetsMinimum
step "Promedio diario de una cuenta sin minimo (meetsMinimum null)"
req GET "$GW/accounts/$PCUR/average-daily-balance?month=$MONTH"; expect 200; eqstr "$(raw meetsMinimum)" null meetsMinimum
step "[400] mes con formato invalido"
req GET "$GW/accounts/$VSAV/average-daily-balance?month=octubre"; expect 400
step "[409] mes anterior a la apertura de la cuenta"
req GET "$GW/accounts/$VSAV/average-daily-balance?month=2020-01"; expect 409; has "no balance history"

echo "=== 08a - Comision por exceso de transacciones (CURRENT PYME: 10 gratis, luego 1.50)"
for i in $(seq 1 10); do
  req POST "$GW/transactions/deposit" "{\"accountId\":\"$PCUR\",\"amount\":10,\"description\":\"Deposito gratis $i\"}"
  [ "$CODE" = "201" ] && awk -v f="$(num feeCharged)" 'BEGIN{exit !(f+0==0)}' || { step "deposito gratis $i"; ko "deposito $i deberia ser 201 sin comision"; }
done; step "10 depositos sin comision"; ok "10 depositos con feeCharged = 0"
step "Deposito 11: lleva comision"
req POST "$GW/transactions/deposit" "{\"accountId\":\"$PCUR\",\"amount\":10,\"description\":\"Deposito 11\"}"
expect 201; eqnum "$(num feeCharged)" 1.5 feeCharged
step "Retiro 12: tambien lleva comision"
req POST "$GW/transactions/withdrawal" "{\"accountId\":\"$PCUR\",\"amount\":20,\"description\":\"Retiro 12\"}"
expect 201; eqnum "$(num feeCharged)" 1.5 feeCharged
step "Saldo PYME = 1000 + 11*10 - 1.50 - 20 - 1.50 = 1087"
req GET "$GW/accounts/$PCUR/balance"; expect 200; eqnum "$(num balance)" 1087 balance

echo "=== 08b - Transferencias"
step "Transferencia a terceros: PYME -> SAVINGS VIP (por numero de cuenta)"
req POST "$GW/transactions/transfer" "{\"sourceAccountId\":\"$PCUR\",\"destinationAccountNumber\":\"$VSAVNUM\",\"amount\":87,\"description\":\"Pago a proveedor\"}"
expect 201; eqstr "$(str transferType)" THIRD_PARTY transferType; has '"transactionType":"TRANSFER_OUT"'; has '"transactionType":"TRANSFER_IN"'
TID=$(str transferId); [ -n "$TID" ] && ok "transferId presente" || ko "falta transferId"
eqnum "$(echo "$BODY" | grep -o "\"transferId\":\"$TID\"" | wc -l)" 3 "transferId repetido en respuesta y en los 2 movimientos"
step "Saldos tras la transferencia (sin comision)"
req GET "$GW/accounts/$PCUR/balance"; eqnum "$(num balance)" 1000 "saldo PYME"
req GET "$GW/accounts/$VSAV/balance"; eqnum "$(num balance)" 2087 "saldo VIP"
step "Transferencia propia: SAVINGS -> CURRENT del mismo cliente"
req POST "$GW/transactions/transfer" "{\"sourceAccountId\":\"$SSAV\",\"destinationAccountNumber\":\"$SCURNUM\",\"amount\":100}"
expect 201; eqstr "$(str transferType)" OWN transferType
step "[409] transferencia sin fondos (no se mueve nada)"
req POST "$GW/transactions/transfer" "{\"sourceAccountId\":\"$SCUR\",\"destinationAccountNumber\":\"$VSAVNUM\",\"amount\":999999}"
expect 409; has "Insufficient funds"
req GET "$GW/accounts/$VSAV/balance"; eqnum "$(num balance)" 2087 "saldo VIP sin cambios"
step "[409] origen y destino iguales"
req POST "$GW/transactions/transfer" "{\"sourceAccountId\":\"$SCUR\",\"destinationAccountNumber\":\"$SCURNUM\",\"amount\":1}"
expect 409; has "must be different"
step "[404] cuenta destino inexistente"
req POST "$GW/transactions/transfer" "{\"sourceAccountId\":\"$SCUR\",\"destinationAccountNumber\":\"00000000000000\",\"amount\":1}"
expect 404
step "Las transferencias no cuentan para la comision (deposito siguiente en SAVINGS STD sin fee)"
req POST "$GW/transactions/deposit" "{\"accountId\":\"$SSAV\",\"amount\":5}"; expect 201; eqnum "$(num feeCharged)" 0 feeCharged

echo "=== 09 - Reportes"
step "Reporte del producto CURRENT PYME (hoy)"
req GET "$GW/reports/products/ACCOUNT/$PCUR?from=$TODAY&to=$TODAY"; expect 200
eqnum "$(num movementCount)" 13 movementCount; eqnum "$(num totalFees)" 3 totalFees; eqnum "$(num currentBalance)" 1000 currentBalance
has '"DEPOSIT":110'; has '"WITHDRAWAL":20'; has '"TRANSFER_OUT":87'
step "Reporte general (hoy)"
req GET "$GW/reports/products?from=$TODAY&to=$TODAY"; expect 200; has '"productType":"ACCOUNT"'; has '"productsWithMovements"'
step "[400] reporte con rango invertido"
req GET "$GW/reports/products?from=$TODAY&to=2020-01-01"; expect 400; has "must not be after"
step "12 consumos con la tarjeta VIP"
for i in $(seq 1 12); do req POST "$GW/transactions/charge" "{\"creditCardId\":\"$VCARD\",\"amount\":$i,\"description\":\"Consumo $i\"}"; done; expect 201
step "Ultimos 10 movimientos de la tarjeta VIP"
req GET "$GW/reports/last-movements?productType=CREDIT_CARD&productId=$VCARD"; expect 200
eqnum "$(count transactionType)" 10 "cantidad"
first=$(echo "$BODY" | grep -o '"amount":[0-9.]*' | head -1 | sed 's/.*://'); last=$(echo "$BODY" | grep -o '"amount":[0-9.]*' | tail -1 | sed 's/.*://')
eqnum "$first" 12 "el primero es el mas reciente (consumo 12)"; eqnum "$last" 3 "el ultimo es el consumo 3"
step "[400] ultimos movimientos de un tipo no soportado"
req GET "$GW/reports/last-movements?productType=ACCOUNT&productId=$PCUR"; expect 400; has "only available for"
step "[404] ultimos movimientos de una tarjeta inexistente"
req GET "$GW/reports/last-movements?productType=CREDIT_CARD&productId=000000000000000000000000"; expect 404

echo "=== 99 - Limpieza"
for a in "$VSAV" "$PCUR" "$SSAV" "$SCUR"; do req DELETE "$GW/accounts/$a"; [ "$CODE" = 204 ] || ko "DELETE cuenta $a"; done
for c in "$VCARD" "$PCARD"; do req DELETE "$GW/credit-cards/$c"; [ "$CODE" = 204 ] || ko "DELETE tarjeta $c"; done
for c in "$VIP" "$PYME" "$STD"; do req DELETE "$GW/customers/$c"; [ "$CODE" = 204 ] || ko "DELETE cliente $c"; done
step "Limpieza"; ok "cuentas, tarjetas y clientes eliminados"

echo; echo "=================== RESULTADO PARTE II: $PASS OK, $FAIL FAIL ==================="
for f in "${FAILS[@]}"; do echo "  - $f"; done
[ "$FAIL" -eq 0 ]

#!/usr/bin/env bash
# Prueba de punta a punta de la Parte III, todo a traves del API Gateway (con JWT).
# Uso: ADMIN_USERNAME=admin ADMIN_PASSWORD='...' bash run-flow-part3.sh
#      (con Kafka, Redis, MongoDB, Config Server, Eureka, los 8 servicios y el Gateway arriba)
GW=${GATEWAY:-http://localhost:8080}
ADMIN_USER=${ADMIN_USERNAME:-admin}
ADMIN_PASS=${ADMIN_PASSWORD:-Admin123!}
WAIT=${EVENT_WAIT:-3}   # segundos para que los eventos de Kafka se propaguen
PASS=0; FAIL=0; FAILS=(); TOKEN=""

req() { # metodo url [body]   (usa $TOKEN si esta definido)
  local out auth=()
  [ -n "$TOKEN" ] && auth=(-H "Authorization: Bearer $TOKEN")
  if [ -n "$3" ]; then out=$(curl -s -w $'\n%{http_code}' -X "$1" "${auth[@]}" -H 'Content-Type: application/json' -d "$3" "$2")
  else out=$(curl -s -w $'\n%{http_code}' -X "$1" "${auth[@]}" "$2"); fi
  CODE="${out##*$'\n'}"; BODY="${out%$'\n'*}"
}
str() { echo "$BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed 's/.*":"//; s/"$//'; }
num() { echo "$BODY" | grep -o "\"$1\":[-0-9.]*" | head -1 | sed 's/.*://'; }
raw() { echo "$BODY" | grep -o "\"$1\":[a-z]*" | head -1 | sed 's/.*://'; }
ok() { PASS=$((PASS+1)); printf '  \033[32mOK\033[0m   %s\n' "$1"; }
ko() { FAIL=$((FAIL+1)); FAILS+=("$CURRENT :: $1"); printf '  \033[31mFAIL\033[0m %s\n         -> HTTP %s %s\n' "$1" "$CODE" "${BODY:0:300}"; }
step() { CURRENT="$1"; echo "* $1"; }
expect() { [ "$CODE" = "$1" ] && ok "HTTP $1" || ko "esperaba HTTP $1"; }
has() { echo "$BODY" | grep -qF -- "$1" && ok "contiene: $1" || ko "deberia contener: $1"; }
eqnum() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a+0==b+0)}' && ok "$3 = $2" || ko "$3: esperaba $2, obtuvo '$1'"; }
eqstr() { [ "$1" = "$2" ] && ok "$3 = $2" || ko "$3: esperaba '$2', obtuvo '$1'"; }
doc() { echo $(( 10000000 + RANDOM * 2000 + RANDOM % 2000 )); }
phone() { echo "9$(( 10000000 + RANDOM * 2000 + RANDOM % 2000 ))"; }
pause() { sleep "$WAIT"; }
login() { # usuario clave -> TOKEN
  TOKEN=""; req POST "$GW/auth/login" "{\"username\":\"$1\",\"password\":\"$2\"}"; TOKEN=$(str accessToken)
}
personal() { # nombre -> id del cliente creado
  req POST "$GW/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"name\":\"$1\"}"
  str id
}

YESTERDAY=$(date -d yesterday +%F 2>/dev/null || date -v-1d +%F)

echo "=== 11 - Seguridad (JWT en el gateway)"
step "Login del administrador"
login "$ADMIN_USER" "$ADMIN_PASS"; expect 200; eqstr "$(str tokenType)" Bearer tokenType
ADMIN_TOKEN=$TOKEN
[ -n "$ADMIN_TOKEN" ] && ok "token recibido" || ko "no se recibio token"
step "[401] login con clave incorrecta: mensaje generico"
login "$ADMIN_USER" "clave-incorrecta"; expect 401; has "Invalid username or password"
step "[401] login con usuario inexistente: mismo mensaje"
login "no-existe-$(doc)" "Secret123"; expect 401; has "Invalid username or password"
step "[401] sin token"
TOKEN=""; req GET "$GW/customers"; expect 401; has '"status":401'
step "[401] token invalido"
TOKEN="abc.def.ghi"; req GET "$GW/customers"; expect 401
step "Registro publico (rol USER)"
USERNAME="user$(doc)"; TOKEN=""
req POST "$GW/auth/register" "{\"username\":\"$USERNAME\",\"password\":\"Secret123\",\"phoneNumber\":\"$(phone)\"}"
expect 201; has '"roles":["USER"]'
echo "$BODY" | grep -q passwordHash && ko "no debe devolver passwordHash" || ok "sin passwordHash"
step "Login del USER"
login "$USERNAME" "Secret123"; expect 200; USER_TOKEN=$TOKEN
step "USER puede leer (GET)"
TOKEN=$USER_TOKEN; req GET "$GW/customers"; expect 200
step "[403] USER no puede actualizar (PUT)"
req PUT "$GW/customers/x" '{"type":"PERSONAL","documentType":"DNI","documentNumber":"1","name":"x"}'
expect 403; has "Insufficient role"
step "[403] USER no puede eliminar (DELETE)"
req DELETE "$GW/customers/x"; expect 403
step "[403] USER no puede administrar usuarios"
req GET "$GW/auth/users"; expect 403
step "[403] USER no puede usar /admin/**"
req POST "$GW/admin/customers/events/republish"; expect 403
step "ADMIN lista usuarios"
TOKEN=$ADMIN_TOKEN; req GET "$GW/auth/users"; expect 200; has "\"username\":\"$USERNAME\""
step "[403] endpoint interno sigue bloqueado (con token)"
req POST "$GW/accounts/x/balance-operations" '{"operation":"DEPOSIT","amount":1}'; expect 403
has "Internal endpoint not exposed"

echo "=== 12 - Eventos y cache de clientes"
TOKEN=$ADMIN_TOKEN
step "Cliente nuevo y busqueda por documento (cache Redis)"
DOCN=$(doc)
req POST "$GW/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$DOCN\",\"name\":\"Cache Test\"}"
expect 201; CACHED=$(str id)
req GET "$GW/customers?documentNumber=$DOCN"; expect 200; has "\"id\":\"$CACHED\""
req GET "$GW/customers/$CACHED"; expect 200
step "Republicar snapshots (ADMIN)"
req POST "$GW/admin/customers/events/republish"; expect 200; has '"eventType":"CUSTOMER_UPSERTED"'
req POST "$GW/admin/accounts/events/republish"; expect 200; has '"eventType":"ACCOUNT_UPSERTED"'

echo "=== 13 - Deuda vencida (regla de adquisicion)"
TOKEN=$ADMIN_TOKEN
step "Cliente con credito"
DEBTOR=$(personal "Deudor Moroso")
req POST "$GW/credits" "{\"creditType\":\"PERSONAL\",\"customerId\":\"$DEBTOR\",\"grantedAmount\":1000,\"interestRate\":10}"
expect 201; CREDIT=$(str id)
step "Fijar cuota vencida con el PUT (ayer, 150)"
req PUT "$GW/credits/$CREDIT" "{\"creditType\":\"PERSONAL\",\"customerId\":\"$DEBTOR\",\"grantedAmount\":1000,\"interestRate\":10,\"dueDate\":\"$YESTERDAY\",\"dueAmount\":150}"
expect 200; eqstr "$(str dueDate)" "$YESTERDAY" dueDate
step "Deteccion de deudas vencidas (ADMIN, en vez de esperar el cron)"
req POST "$GW/admin/credits/overdue-debts/scan"; expect 200; has '"productType":"CREDIT"'
req GET "$GW/credits/$CREDIT"; eqstr "$(raw overdueNotified)" true overdueNotified
pause
step "[409] cliente con deuda vencida no puede abrir cuenta"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$DEBTOR\",\"openingAmount\":100}"
expect 409; has "overdue debt"
step "[409] ni sacar tarjeta de credito"
req POST "$GW/credit-cards" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$DEBTOR\",\"creditLimit\":1000}"
expect 409; has "overdue debt"
step "Pago de terceros que salda la cuota"
PAYER=$(personal "Pagador Tercero")
req POST "$GW/transactions/payment" "{\"productType\":\"CREDIT\",\"productId\":\"$CREDIT\",\"amount\":150,\"payerCustomerId\":\"$PAYER\",\"description\":\"pago de un tercero\"}"
expect 201; eqstr "$(str payerCustomerId)" "$PAYER" payerCustomerId; eqstr "$(raw thirdParty)" true thirdParty
eqstr "$(str customerId)" "$DEBTOR" "customerId (titular)"
req GET "$GW/credits/$CREDIT"; eqnum "$(num dueAmount)" 0 dueAmount; eqstr "$(raw overdueNotified)" false overdueNotified
pause
step "Deuda saldada: ya puede abrir cuenta"
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$DEBTOR\",\"openingAmount\":100}"
expect 201

echo "=== 14 - Pago de terceros"
TOKEN=$ADMIN_TOKEN
step "Pago del propio titular: thirdParty=false"
req POST "$GW/transactions/payment" "{\"productType\":\"CREDIT\",\"productId\":\"$CREDIT\",\"amount\":10}"
expect 201; eqstr "$(raw thirdParty)" false thirdParty; eqstr "$(str payerCustomerId)" "$DEBTOR" payerCustomerId
step "[404] pagador inexistente"
req POST "$GW/transactions/payment" "{\"productType\":\"CREDIT\",\"productId\":\"$CREDIT\",\"amount\":10,\"payerCustomerId\":\"000000000000000000000000\"}"
expect 404; has "Customer not found"

echo "=== 15 - Tarjeta de debito"
TOKEN=$ADMIN_TOKEN
step "Cliente con cuenta de ahorro (50) y corriente (500)"
HOLDER=$(personal "Titular Debito")
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$HOLDER\",\"openingAmount\":50}"
expect 201; SAV=$(str id)
req POST "$GW/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$HOLDER\",\"openingAmount\":500}"
expect 201; CUR=$(str id)
OTHER=$(personal "Otro Cliente")
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$OTHER\",\"openingAmount\":10}"
OTHER_ACC=$(str id)
pause
step "[409] cuenta de otro cliente"
req POST "$GW/debit-cards" "{\"customerId\":\"$HOLDER\",\"accountIds\":[\"$SAV\",\"$OTHER_ACC\"],\"mainAccountId\":\"$SAV\"}"
expect 409; has "does not belong"
step "[409] principal fuera de las cuentas asociadas"
req POST "$GW/debit-cards" "{\"customerId\":\"$HOLDER\",\"accountIds\":[\"$SAV\"],\"mainAccountId\":\"$CUR\"}"
expect 409; has "mainAccountId must be one of accountIds"
step "[404] cliente inexistente en la copia local"
req POST "$GW/debit-cards" "{\"customerId\":\"000000000000000000000000\",\"accountIds\":[\"$SAV\"],\"mainAccountId\":\"$SAV\"}"
expect 404
step "Crear tarjeta: ahorro (principal) + corriente"
req POST "$GW/debit-cards" "{\"customerId\":\"$HOLDER\",\"accountIds\":[\"$SAV\",\"$CUR\"],\"mainAccountId\":\"$SAV\"}"
expect 201; CARD=$(str id); eqstr "$(str status)" ACTIVE status
echo "$(str cardNumber)" | grep -qE '^5555[0-9]{12}$' && ok "cardNumber de 16 digitos" || ko "cardNumber invalido"
step "Saldo de la tarjeta = saldo de la cuenta principal"
req GET "$GW/accounts/$SAV/balance"; eqnum "$(num balance)" 50 balance
step "Pago de 80 con fallback (ahorro sin fondos -> corriente): 202 PENDING"
req POST "$GW/debit-cards/$CARD/payments" '{"amount":80,"description":"supermercado"}'
expect 202; eqstr "$(str status)" PENDING status; PAY=$(str paymentId)
pause
req GET "$GW/debit-cards/$CARD/payments/$PAY"; expect 200
eqstr "$(str status)" COMPLETED status; eqstr "$(str chargedAccountId)" "$CUR" chargedAccountId
has "\"attemptedAccountIds\":[\"$SAV\",\"$CUR\"]"
req GET "$GW/accounts/$SAV/balance"; eqnum "$(num balance)" 50 "saldo ahorro (sin tocar)"
req GET "$GW/accounts/$CUR/balance"; eqnum "$(num balance)" 420 "saldo corriente"
step "Pago de 1000: ninguna cuenta alcanza -> FAILED"
req POST "$GW/debit-cards/$CARD/payments" '{"amount":1000}'; expect 202; PAY2=$(str paymentId)
pause
req GET "$GW/debit-cards/$CARD/payments/$PAY2"; eqstr "$(str status)" FAILED status
eqstr "$(str failureReason)" INSUFFICIENT_FUNDS failureReason
step "Historial de pagos de la tarjeta"
req GET "$GW/debit-cards/$CARD/payments"; expect 200; has "\"paymentId\":\"$PAY\""
step "Ultimos movimientos de la tarjeta de debito (reporte)"
req GET "$GW/reports/last-movements?productType=DEBIT_CARD&productId=$CARD"; expect 200
has '"transactionType":"DEBIT_PAYMENT"'; has '"origin":"DEBIT_CARD"'; has "\"accountId\":\"$CUR\""
step "Cambiar la cuenta principal"
req PUT "$GW/debit-cards/$CARD/main-account" "{\"accountId\":\"$CUR\"}"; expect 200
eqstr "$(str mainAccountId)" "$CUR" mainAccountId
step "Eliminar la cuenta principal: pasa a serlo la siguiente"
req DELETE "$GW/accounts/$CUR"; expect 204
pause
req GET "$GW/debit-cards/$CARD"; eqstr "$(str mainAccountId)" "$SAV" mainAccountId; eqstr "$(str status)" ACTIVE status
step "Eliminar la ultima cuenta: tarjeta INACTIVE y no admite pagos"
req DELETE "$GW/accounts/$SAV"; expect 204
pause
req GET "$GW/debit-cards/$CARD"; eqstr "$(str status)" INACTIVE status
req POST "$GW/debit-cards/$CARD/payments" '{"amount":5}'; expect 409; has "is not active"
step "[409] cliente con deuda vencida de tarjeta de credito no puede sacar tarjeta de debito"
MOROSO=$(personal "Moroso Tarjeta")
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$MOROSO\",\"openingAmount\":10}"; MACC=$(str id)
req POST "$GW/credit-cards" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$MOROSO\",\"creditLimit\":1000}"; CC=$(str id)
req PUT "$GW/credit-cards/$CC" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$MOROSO\",\"creditLimit\":1000,\"paymentDueDate\":\"$YESTERDAY\",\"dueAmount\":200}"
expect 200
req POST "$GW/admin/credit-cards/overdue-debts/scan"; expect 200; has '"productType":"CREDIT_CARD"'
pause
req POST "$GW/debit-cards" "{\"customerId\":\"$MOROSO\",\"accountIds\":[\"$MACC\"],\"mainAccountId\":\"$MACC\"}"
expect 409; has "overdue debt"

echo "=== 16 - Monedero Yanki"
TOKEN=$ADMIN_TOKEN
wallet() { # celular -> id del monedero
  req POST "$GW/yanki/wallets" "{\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"phoneNumber\":\"$1\",\"imei\":\"356938035643809\",\"email\":\"yanki@mail.com\"}"
  str id
}
step "Monederos sin tarjeta (no requieren ser clientes)"
PA=$(phone); PB=$(phone); PC=$(phone); PD=$(phone)
WA=$(wallet "$PA"); [ -n "$WA" ] && ok "monedero A creado" || ko "no se creo el monedero A"
req GET "$GW/yanki/wallets/$WA"; eqnum "$(num balance)" 0 "saldo inicial"
WC=$(wallet "$PC"); [ -n "$WC" ] && ok "monedero C creado" || ko "no se creo el monedero C"
step "[409] celular repetido"
req POST "$GW/yanki/wallets" "{\"documentType\":\"DNI\",\"documentNumber\":\"$(doc)\",\"phoneNumber\":\"$PA\",\"imei\":\"1\",\"email\":\"a@b.com\"}"
expect 409
step "Monedero B asociado a una tarjeta de debito (cuenta principal con 300)"
CB=$(personal "Cliente Yanki")
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$CB\",\"openingAmount\":300}"; BACC=$(str id)
pause
req POST "$GW/debit-cards" "{\"customerId\":\"$CB\",\"accountIds\":[\"$BACC\"],\"mainAccountId\":\"$BACC\"}"
expect 201; BCARD_NUMBER=$(str cardNumber)
WB=$(wallet "$PB")
pause
req PUT "$GW/yanki/wallets/$WB/debit-card" "{\"cardNumber\":\"$BCARD_NUMBER\"}"
expect 200; eqstr "$(str debitCardNumber)" "$BCARD_NUMBER" debitCardNumber
step "B (con tarjeta) paga 50 a A (sin tarjeta): 202 y luego COMPLETED"
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PB\",\"toPhoneNumber\":\"$PA\",\"amount\":50,\"description\":\"almuerzo\"}"
expect 202; eqstr "$(str status)" PENDING status; YP1=$(str paymentId)
pause
req GET "$GW/yanki/payments/$YP1"; eqstr "$(str status)" COMPLETED status
req GET "$GW/accounts/$BACC/balance"; eqnum "$(num balance)" 250 "saldo cuenta principal de B"
req GET "$GW/yanki/wallets/$WA"; eqnum "$(num balance)" 50 "saldo propio de A"
step "El movimiento Yanki queda registrado en la cuenta de B"
req GET "$GW/transactions?productId=$BACC"; has '"origin":"YANKI"'; has '"transactionType":"WITHDRAWAL"'
step "A (sin tarjeta) paga 20 a C (sin tarjeta): se completa al instante"
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"$PC\",\"amount\":20}"
expect 202; eqstr "$(str status)" COMPLETED status
req GET "$GW/yanki/wallets/$WA"; eqnum "$(num balance)" 30 "saldo de A"
req GET "$GW/yanki/wallets/$WC"; eqnum "$(num balance)" 20 "saldo de C"
step "A paga 10 a B (con tarjeta): se acredita en la cuenta principal de B"
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"$PB\",\"amount\":10}"
expect 202; YP3=$(str paymentId)
pause
req GET "$GW/yanki/payments/$YP3"; eqstr "$(str status)" COMPLETED status
req GET "$GW/accounts/$BACC/balance"; eqnum "$(num balance)" 260 "saldo cuenta principal de B"
step "[409] saldo propio insuficiente"
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"$PC\",\"amount\":1000}"
expect 409; has "Insufficient wallet balance"
step "[409] mismo celular / [404] celular inexistente"
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"$PA\",\"amount\":1}"; expect 409
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"900000001\",\"amount\":1}"; expect 404
step "Compensacion: el receptor D tiene una tarjeta que quedo inactiva"
CD=$(personal "Cliente D")
req POST "$GW/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$CD\",\"openingAmount\":10}"; DACC=$(str id)
pause
req POST "$GW/debit-cards" "{\"customerId\":\"$CD\",\"accountIds\":[\"$DACC\"],\"mainAccountId\":\"$DACC\"}"; DCARD_NUMBER=$(str cardNumber)
WD=$(wallet "$PD")
pause
req PUT "$GW/yanki/wallets/$WD/debit-card" "{\"cardNumber\":\"$DCARD_NUMBER\"}"; expect 200
req DELETE "$GW/accounts/$DACC"; expect 204
pause
req POST "$GW/yanki/payments" "{\"fromPhoneNumber\":\"$PA\",\"toPhoneNumber\":\"$PD\",\"amount\":5}"
expect 202; eqstr "$(str status)" FAILED status; eqstr "$(str failureReason)" RECEIVER_CARD_NOT_ACTIVE failureReason
req GET "$GW/yanki/wallets/$WA"; eqnum "$(num balance)" 20 "saldo de A devuelto"
step "[409] asociar una tarjeta inactiva"
req PUT "$GW/yanki/wallets/$WA/debit-card" "{\"cardNumber\":\"$DCARD_NUMBER\"}"; expect 409
step "Historial por celular"
req GET "$GW/yanki/payments?phoneNumber=$PA"; expect 200; has "\"paymentId\":\"$YP3\""

echo
echo "=================== RESULTADO PARTE III: $PASS OK, $FAIL FAIL ==================="
for f in "${FAILS[@]}"; do echo "  - $f"; done
[ "$FAIL" -eq 0 ]

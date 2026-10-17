#!/usr/bin/env bash
# Replica 1:1 la coleccion bank.postman_collection.json con curl (para correr sin Postman).
# Uso: bash run-flow.sh                                (directo a cada servicio)
#      GATEWAY=http://localhost:8080 bash run-flow.sh  (todo a traves del API Gateway)
CFG=http://localhost:8888; CUS=http://localhost:8081; ACC=http://localhost:8082
CRE=http://localhost:8083; CARD=http://localhost:8084; TX=http://localhost:8085
if [ -n "$GATEWAY" ]; then CUS=$GATEWAY; ACC=$GATEWAY; CRE=$GATEWAY; CARD=$GATEWAY; TX=$GATEWAY; fi
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
ok() { PASS=$((PASS+1)); printf '  \033[32mOK\033[0m   %s\n' "$1"; }
ko() { FAIL=$((FAIL+1)); FAILS+=("$CURRENT :: $1"); printf '  \033[31mFAIL\033[0m %s\n         -> HTTP %s %s\n' "$1" "$CODE" "${BODY:0:300}"; }
step() { CURRENT="$1"; echo "* $1"; }
expect() { [ "$CODE" = "$1" ] && ok "HTTP $1" || ko "esperaba HTTP $1"; }
has() { echo "$BODY" | grep -qF -- "$1" && ok "contiene: $1" || ko "deberia contener: $1"; }
eqnum() { awk -v a="$1" -v b="$2" 'BEGIN{exit !(a+0==b+0)}' && ok "$3 = $2" || ko "$3: esperaba $2, obtuvo '$1'"; }
eqstr() { [ "$1" = "$2" ] && ok "$3 = $2" || ko "$3: esperaba '$2', obtuvo '$1'"; }

PDOC=$(( 10000000 + RANDOM * 2000 + RANDOM % 2000 )); BDOC="20$(( 100000000 + RANDOM * 20000 + RANDOM ))"
TODAY=$(date +%-d); MDAY=$(( TODAY < 28 ? TODAY : 28 )); OPEN=$([ "$TODAY" -le 28 ] && echo true || echo false)

echo "=== 00 - Config Server"
step "config customer";    req GET "$CFG/bank-customer-service/default"; expect 200; has '"propertySources":[{'
step "config transaction"; req GET "$CFG/bank-transaction-service/default"; expect 200

echo "=== 01 - Clientes"
step "Crear cliente PERSONAL"
req POST "$CUS/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$PDOC\",\"name\":\"Ana Torres\",\"email\":\"ana.torres@example.com\",\"phoneNumber\":\"999888777\",\"address\":\"Av. Siempre Viva 123\"}"
expect 201; eqstr "$(str profile)" STANDARD profile; PCUS=$(str id)
step "Crear cliente BUSINESS"
req POST "$CUS/customers" "{\"type\":\"BUSINESS\",\"documentType\":\"RUC\",\"documentNumber\":\"$BDOC\",\"name\":\"Comercial Andina SAC\",\"email\":\"contacto@andina.example.com\",\"phoneNumber\":\"014445566\",\"address\":\"Jr. Comercio 456\"}"
expect 201; BCUS=$(str id)
step "[409] documento duplicado"
req POST "$CUS/customers" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$PDOC\",\"name\":\"Otra Persona\"}"
expect 409; has "already exists"
step "[400] sin campos obligatorios"
req POST "$CUS/customers" '{"name":"Sin tipo ni documento","email":"no-es-un-email"}'
expect 400; for k in timestamp status error message path; do has "\"$k\""; done
step "Listar clientes"; req GET "$CUS/customers"; expect 200; has "$PCUS"; has "$BCUS"
step "Buscar PERSONAL por id"; req GET "$CUS/customers/$PCUS"; expect 200; eqstr "$(str type)" PERSONAL type
step "Actualizar PERSONAL"
req PUT "$CUS/customers/$PCUS" "{\"type\":\"PERSONAL\",\"documentType\":\"DNI\",\"documentNumber\":\"$PDOC\",\"name\":\"Ana Torres\",\"email\":\"ana.nueva@example.com\",\"phoneNumber\":\"999888777\",\"address\":\"Calle Nueva 789\"}"
expect 200; eqstr "$(str email)" ana.nueva@example.com email
step "[404] cliente inexistente"; req GET "$CUS/customers/000000000000000000000000"; expect 404

echo "=== 02 - Cuentas"
step "SAVINGS personal (limite 2)"
req POST "$ACC/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$PCUS\",\"initialBalance\":500.00,\"monthlyMovementLimit\":2}"
expect 201; eqnum "$(num maintenanceFee)" 0 maintenanceFee
[[ "$(str accountNumber)" =~ ^[0-9]{14}$ ]] && ok "accountNumber de 14 digitos" || ko "accountNumber invalido"
has "\"documentNumber\":\"$PDOC\""; SAV=$(str id)
step "[409] segunda SAVINGS"
req POST "$ACC/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$PCUS\",\"initialBalance\":100.00}"
expect 409; has "only hold one SAVINGS"
step "CURRENT personal"
req POST "$ACC/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$PCUS\",\"initialBalance\":300.00}"
expect 201; awk -v a="$(num maintenanceFee)" 'BEGIN{exit !(a>0)}' && ok "comision > 0" || ko "comision deberia ser > 0"
has '"monthlyMovementLimit":null'; PCUR=$(str id)
step "FIXED_TERM personal (movementDay=$MDAY)"
req POST "$ACC/accounts" "{\"accountType\":\"FIXED_TERM\",\"customerId\":\"$PCUS\",\"initialBalance\":2000.00,\"movementDay\":$MDAY}"
expect 201; eqnum "$(num maintenanceFee)" 0 maintenanceFee; eqnum "$(num monthlyMovementLimit)" 1 monthlyMovementLimit; FIX=$(str id)
step "[409] FIXED_TERM sin movementDay"
req POST "$ACC/accounts" "{\"accountType\":\"FIXED_TERM\",\"customerId\":\"$PCUS\",\"initialBalance\":1000.00}"
expect 409; has "movementDay is required"
step "CURRENT empresarial con titulares y firmantes"
req POST "$ACC/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$BCUS\",\"initialBalance\":1000.00,\"holders\":[{\"documentType\":\"DNI\",\"documentNumber\":\"45678912\",\"fullName\":\"Ana Torres\"},{\"documentType\":\"DNI\",\"documentNumber\":\"41234567\",\"fullName\":\"Jorge Rios\"}],\"authorizedSigners\":[{\"documentType\":\"DNI\",\"documentNumber\":\"10293847\",\"fullName\":\"Luis Paz\"}]}"
expect 201; has "41234567"; has "10293847"; BCUR=$(str id)
step "[409] SAVINGS empresarial"
req POST "$ACC/accounts" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$BCUS\",\"initialBalance\":100.00,\"holders\":[{\"documentType\":\"DNI\",\"documentNumber\":\"45678912\",\"fullName\":\"Ana Torres\"}]}"
expect 409; has "only hold CURRENT"
step "[409] empresarial sin titulares"
req POST "$ACC/accounts" "{\"accountType\":\"CURRENT\",\"customerId\":\"$BCUS\",\"initialBalance\":100.00}"
expect 409; has "at least one holder"
step "[404] cuenta para cliente inexistente"
req POST "$ACC/accounts" '{"accountType":"SAVINGS","customerId":"000000000000000000000000","initialBalance":100.00}'
expect 404
step "Listar cuentas del PERSONAL"
req GET "$ACC/accounts?customerId=$PCUS"; expect 200
types=$(echo "$BODY" | grep -o '"accountType":"[A-Z_]*"' | sed 's/.*:"//; s/"//' | sort | tr '\n' ' ')
eqstr "$types" "CURRENT FIXED_TERM SAVINGS " tipos
step "Actualizar comision empresarial"
req PUT "$ACC/accounts/$BCUR" "{\"accountType\":\"CURRENT\",\"customerId\":\"$BCUS\",\"maintenanceFee\":15.00}"
expect 200; eqnum "$(num maintenanceFee)" 15 maintenanceFee; eqnum "$(num balance)" 1000 balance
step "[409] cambiar tipo de cuenta"
req PUT "$ACC/accounts/$BCUR" "{\"accountType\":\"SAVINGS\",\"customerId\":\"$BCUS\"}"
expect 409; has "accountType cannot be modified"
step "Saldo SAVINGS = 500"; req GET "$ACC/accounts/$SAV/balance"; expect 200; eqnum "$(num balance)" 500 balance

echo "=== 03 - Creditos"
step "Credito PERSONAL 5000"
req POST "$CRE/credits" "{\"creditType\":\"PERSONAL\",\"customerId\":\"$PCUS\",\"grantedAmount\":5000.00,\"interestRate\":12.5}"
expect 201; eqnum "$(num outstandingBalance)" "$(num grantedAmount)" outstandingBalance; PCRE=$(str id)
step "[409] segundo credito PERSONAL"
req POST "$CRE/credits" "{\"creditType\":\"PERSONAL\",\"customerId\":\"$PCUS\",\"grantedAmount\":1000.00,\"interestRate\":10}"
expect 409; has "credit(s)"
step "[409] credito PERSONAL para BUSINESS"
req POST "$CRE/credits" "{\"creditType\":\"PERSONAL\",\"customerId\":\"$BCUS\",\"grantedAmount\":20000.00,\"interestRate\":9}"
expect 409; has "only hold BUSINESS credits"
step "Credito BUSINESS 20000"
req POST "$CRE/credits" "{\"creditType\":\"BUSINESS\",\"customerId\":\"$BCUS\",\"grantedAmount\":20000.00,\"interestRate\":9}"
expect 201; BCRE=$(str id)
step "Listar creditos BUSINESS"
req GET "$CRE/credits?customerId=$BCUS"; expect 200
eqnum "$(echo "$BODY" | grep -o '"creditType"' | wc -l)" 1 "cantidad"; has '"creditType":"BUSINESS"'

echo "=== 04 - Tarjetas"
step "Tarjeta PERSONAL linea 1000"
req POST "$CARD/credit-cards" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$PCUS\",\"creditLimit\":1000.00}"
expect 201; [[ "$(str cardNumber)" =~ ^[0-9]{16}$ ]] && ok "cardNumber de 16 digitos" || ko "cardNumber invalido"
eqnum "$(num availableBalance)" 1000 availableBalance; PCARD=$(str id)
step "[409] tarjeta PERSONAL para BUSINESS"
req POST "$CARD/credit-cards" "{\"cardType\":\"PERSONAL\",\"customerId\":\"$BCUS\",\"creditLimit\":5000.00}"
expect 409; has "only hold BUSINESS credit cards"

echo "=== 05 - Movimientos"
step "Deposito 100 en SAVINGS (1/2)"
req POST "$TX/transactions/deposit" "{\"accountId\":\"$SAV\",\"amount\":100.00,\"description\":\"Deposito en ventanilla\"}"
expect 201; eqstr "$(str transactionType)" DEPOSIT transactionType; eqstr "$(str productType)" ACCOUNT productType
eqstr "$(str customerId)" "$PCUS" customerId; DEPTX=$(str id)
step "[409] retiro mayor al saldo"
req POST "$TX/transactions/withdrawal" "{\"accountId\":\"$SAV\",\"amount\":10000.00,\"description\":\"Retiro sin fondos\"}"
expect 409; has "Insufficient funds"
step "Retiro 50 de SAVINGS (2/2)"
req POST "$TX/transactions/withdrawal" "{\"accountId\":\"$SAV\",\"amount\":50.00,\"description\":\"Retiro en cajero\"}"
expect 201; eqstr "$(str transactionType)" WITHDRAWAL transactionType
step "[409] tercer movimiento SAVINGS"
req POST "$TX/transactions/deposit" "{\"accountId\":\"$SAV\",\"amount\":10.00,\"description\":\"Excede el limite\"}"
expect 409; has "monthly movement limit"
step "Saldo SAVINGS = 550"; req GET "$ACC/accounts/$SAV/balance"; expect 200; eqnum "$(num balance)" 550 balance
step "Movimientos SAVINGS = 2"
req GET "$TX/transactions?productId=$SAV"; expect 200; eqnum "$(echo "$BODY" | grep -o '"transactionType"' | wc -l)" 2 movimientos
step "Deposito en CURRENT"
req POST "$TX/transactions/deposit" "{\"accountId\":\"$PCUR\",\"amount\":75.00,\"description\":\"Abono de sueldo\"}"; expect 201
step "Deposito en FIXED_TERM en su dia (hoy=$TODAY)"
req POST "$TX/transactions/deposit" "{\"accountId\":\"$FIX\",\"amount\":500.00,\"description\":\"Aporte a plazo fijo\"}"
[ "$OPEN" = true ] && expect 201 || expect 409
step "[409] segundo movimiento FIXED_TERM"
req POST "$TX/transactions/withdrawal" "{\"accountId\":\"$FIX\",\"amount\":100.00,\"description\":\"Segundo movimiento del mes\"}"; expect 409
step "Consumo 80 con tarjeta"
req POST "$TX/transactions/charge" "{\"creditCardId\":\"$PCARD\",\"amount\":80.00,\"description\":\"Compra en supermercado\"}"
expect 201; eqstr "$(str transactionType)" CHARGE transactionType; eqstr "$(str productType)" CREDIT_CARD productType
step "[409] consumo excedido"
req POST "$TX/transactions/charge" "{\"creditCardId\":\"$PCARD\",\"amount\":5000.00,\"description\":\"Compra que excede la linea\"}"
expect 409; has "exceeds the available balance"
step "Pago 30 de tarjeta"
req POST "$TX/transactions/payment" "{\"productType\":\"CREDIT_CARD\",\"productId\":\"$PCARD\",\"amount\":30.00,\"description\":\"Pago de tarjeta\"}"
expect 201; eqstr "$(str transactionType)" PAYMENT transactionType
step "Saldo tarjeta"; req GET "$CARD/credit-cards/$PCARD/balance"; expect 200
eqnum "$(num usedAmount)" 50 usedAmount; eqnum "$(num availableBalance)" 950 availableBalance
step "Pago 500 credito"
req POST "$TX/transactions/payment" "{\"productType\":\"CREDIT\",\"productId\":\"$PCRE\",\"amount\":500.00,\"description\":\"Cuota del mes\"}"
expect 201; eqstr "$(str productType)" CREDIT productType
step "[409] pago excesivo de credito"
req POST "$TX/transactions/payment" "{\"productType\":\"CREDIT\",\"productId\":\"$PCRE\",\"amount\":99999.00,\"description\":\"Pago excesivo\"}"
expect 409; has "exceeds the outstanding balance"
step "Saldo credito = 4500"; req GET "$CRE/credits/$PCRE/balance"; expect 200; eqnum "$(num outstandingBalance)" 4500 outstandingBalance
step "[409] pago a ACCOUNT"
req POST "$TX/transactions/payment" "{\"productType\":\"ACCOUNT\",\"productId\":\"$SAV\",\"amount\":10.00}"
expect 409; has "only apply to CREDIT"
step "[404] deposito en cuenta inexistente"
req POST "$TX/transactions/deposit" '{"accountId":"000000000000000000000000","amount":10.00}'; expect 404
step "Buscar movimiento por id"; req GET "$TX/transactions/$DEPTX"; expect 200; eqnum "$(num amount)" 100 amount
step "Corregir descripcion"
req PUT "$TX/transactions/$DEPTX" '{"description":"Deposito en ventanilla (corregido)"}'
expect 200; eqstr "$(str description)" "Deposito en ventanilla (corregido)" description

echo "=== 99 - Limpieza"
step "DELETE movimiento"; req DELETE "$TX/transactions/$DEPTX"; expect 204
step "[404] movimiento eliminado"; req GET "$TX/transactions/$DEPTX"; expect 404
step "DELETE tarjeta"; req DELETE "$CARD/credit-cards/$PCARD"; expect 204
step "DELETE credito personal"; req DELETE "$CRE/credits/$PCRE"; expect 204
step "DELETE credito empresarial"; req DELETE "$CRE/credits/$BCRE"; expect 204
for a in "$SAV" "$PCUR" "$FIX" "$BCUR"; do step "DELETE cuenta $a"; req DELETE "$ACC/accounts/$a"; expect 204; done
step "DELETE cliente PERSONAL"; req DELETE "$CUS/customers/$PCUS"; expect 204
step "DELETE cliente BUSINESS"; req DELETE "$CUS/customers/$BCUS"; expect 204
step "[404] cliente eliminado"; req GET "$CUS/customers/$PCUS"; expect 404

echo; echo "=================== RESULTADO: $PASS OK, $FAIL FAIL ==================="
for f in "${FAILS[@]}"; do echo "  - $f"; done
[ "$FAIL" -eq 0 ]

#!/bin/sh
# Bootstrap do Vault de DESENVOLVIMENTO: init (1 chave), unseal, Transit + KEK, política e token da aplicação.
# NUNCA use em produção: aqui a unseal key e o root token ficam gravados em volume local.
set -eu

STATE_DIR=/vault/state
SECRETS_DIR=/vault/secrets
KEY_NAME=customer-account-kek
mkdir -p "$STATE_DIR" "$SECRETS_DIR"

echo "Aguardando o Vault em $VAULT_ADDR..."
until vault status >/dev/null 2>&1 || [ $? -eq 2 ]; do
  sleep 2
done

if ! vault status -format=json 2>/dev/null | grep -q '"initialized": true'; then
  echo "Inicializando o Vault..."
  vault operator init -key-shares=1 -key-threshold=1 > "$STATE_DIR/init.txt"
fi

UNSEAL_KEY=$(grep 'Unseal Key 1:' "$STATE_DIR/init.txt" | awk '{print $NF}')
ROOT_TOKEN=$(grep 'Initial Root Token:' "$STATE_DIR/init.txt" | awk '{print $NF}')

if vault status -format=json 2>/dev/null | grep -q '"sealed": true'; then
  echo "Destravando (unseal)..."
  vault operator unseal "$UNSEAL_KEY" >/dev/null
fi

export VAULT_TOKEN="$ROOT_TOKEN"

vault secrets list -format=json | grep -q '"transit/"' || vault secrets enable transit
vault read "transit/keys/$KEY_NAME" >/dev/null 2>&1 \
  || vault write -f "transit/keys/$KEY_NAME" type=aes256-gcm96

# KV v2 para credenciais dos provedores (providers/<provedor>/default | providers/<provedor>/tenants/<tenant>).
vault secrets list -format=json | grep -q '"secret/"' || vault secrets enable -path=secret kv-v2

vault policy write customer-account /vault/scripts/customer-account-policy.hcl
vault token create -policy=customer-account -period=768h -orphan -field=token > "$SECRETS_DIR/app-token"
chmod 644 "$SECRETS_DIR/app-token"

echo "Vault pronto: Transit + KEK '$KEY_NAME', KV 'secret/' e token da aplicação emitido."

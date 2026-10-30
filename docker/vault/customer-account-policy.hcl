# Menor privilégio: a aplicação só gera DEKs e desembrulha DEKs com a KEK; não lê, exporta nem rotaciona a KEK.
path "transit/datakey/plaintext/customer-account-kek" {
  capabilities = ["update"]
}

path "transit/decrypt/customer-account-kek" {
  capabilities = ["update"]
}

# Credenciais dos provedores de validação: somente leitura (escrita fica com o time de operações).
path "secret/data/customer-account/*" {
  capabilities = ["read"]
}

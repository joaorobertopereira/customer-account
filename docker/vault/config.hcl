storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  # Somente desenvolvimento. Em produção: TLS obrigatório (ou HSM/KMS gerenciado).
  tls_disable = 1
}

api_addr      = "http://vault:8200"
ui            = true
disable_mlock = true

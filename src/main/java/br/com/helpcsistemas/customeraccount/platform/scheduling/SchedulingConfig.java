package br.com.helpcsistemas.customeraccount.platform.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tarefas periódicas (expiração de desafios, entrega de webhooks). Cada tarefa é segura com várias instâncias. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}

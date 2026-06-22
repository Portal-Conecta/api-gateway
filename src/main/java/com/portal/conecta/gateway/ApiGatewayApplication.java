package com.portal.conecta.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do API Gateway do Portal Conecta.
 *
 * <p>A anotacao {@link ConfigurationPropertiesScan} habilita o carregamento das
 * configuracoes tipadas usadas por seguranca, rate limit e rotas.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    /**
     * Construtor exigido pelo Spring para registrar a classe como configuracao
     * principal da aplicacao.
     */
    public ApiGatewayApplication() {
    }

    /**
     * Inicializa a aplicacao Spring Boot do gateway.
     *
     * @param args argumentos recebidos pela JVM na inicializacao do processo
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

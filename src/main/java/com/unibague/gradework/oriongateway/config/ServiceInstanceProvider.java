package com.unibague.gradework.oriongateway.config;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de Load Balancer por servicio específico
 */
@Configuration
@LoadBalancerClients({
        @LoadBalancerClient(name = "orion-program", configuration = ServiceInstanceProvider.ProgramConfig.class),
        @LoadBalancerClient(name = "orion-user", configuration = ServiceInstanceProvider.UserConfig.class),
        @LoadBalancerClient(name = "orion-auth", configuration = ServiceInstanceProvider.AuthConfig.class)
})
public class ServiceInstanceProvider {

    // Configuración para Program Service
    // Configuración para Program Service
    static class ProgramConfig {
        @Bean
        public ServiceInstanceListSupplier programServiceInstanceListSupplier() {  // ← Nuevo nombre del método
            return new StaticServiceInstanceListSupplier("orion-program", Arrays.asList(
                    new DefaultServiceInstance("program-1", "orion-program", "localhost", 8093, false)
            ));
        }
    }

    // Configuración para User Service
    static class UserConfig {
        @Bean
        public ServiceInstanceListSupplier userServiceInstanceListSupplier() {  // ← Nuevo nombre del método
            return new StaticServiceInstanceListSupplier("orion-user", Arrays.asList(
                    new DefaultServiceInstance("user-1", "orion-user", "localhost", 8092, false)
            ));
        }
    }

    // Configuración para Auth Service
    static class AuthConfig {
        @Bean
        public ServiceInstanceListSupplier authServiceInstanceListSupplier() {  // ← Nuevo nombre del método
            return new StaticServiceInstanceListSupplier("orion-auth", Arrays.asList(
                    new DefaultServiceInstance("auth-1", "orion-auth", "localhost", 8091, false)
            ));
        }
    }

    // Implementación reutilizable
    static class StaticServiceInstanceListSupplier implements ServiceInstanceListSupplier {
        private final String serviceId;
        private final List<ServiceInstance> instances;

        public StaticServiceInstanceListSupplier(String serviceId, List<ServiceInstance> instances) {
            this.serviceId = serviceId;
            this.instances = instances;
        }

        @Override
        public String getServiceId() {
            return serviceId;
        }

        @Override
        public Flux<List<ServiceInstance>> get() {
            return Flux.just(instances);
        }
    }
}
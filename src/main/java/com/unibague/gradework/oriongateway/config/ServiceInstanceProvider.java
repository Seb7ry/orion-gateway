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
 * Load balancer configuration for specific services
 * Provides static service instance configurations for microservices discovery
 */
@Configuration
@LoadBalancerClients({
        @LoadBalancerClient(name = "orion-program", configuration = ServiceInstanceProvider.ProgramConfig.class),
        @LoadBalancerClient(name = "orion-user", configuration = ServiceInstanceProvider.UserConfig.class),
        @LoadBalancerClient(name = "orion-auth", configuration = ServiceInstanceProvider.AuthConfig.class)
})
public class ServiceInstanceProvider {

    /**
     * Configuration for Program Service instances
     */
    static class ProgramConfig {
        /**
         * Provides static instances for the program service
         * @return ServiceInstanceListSupplier for program service
         */
        @Bean
        public ServiceInstanceListSupplier programServiceInstanceListSupplier() {
            return new StaticServiceInstanceListSupplier("orion-program", Arrays.asList(
                    new DefaultServiceInstance("program-1", "orion-program", "orion-program", 8093, false)
            ));
        }
    }

    /**
     * Configuration for User Service instances
     */
    static class UserConfig {
        /**
         * Provides static instances for the user service
         * @return ServiceInstanceListSupplier for user service
         */
        @Bean
        public ServiceInstanceListSupplier userServiceInstanceListSupplier() {
            return new StaticServiceInstanceListSupplier("orion-user", Arrays.asList(
                    new DefaultServiceInstance("user-1", "orion-user", "orion-user", 8092, false)
            ));
        }
    }

    /**
     * Configuration for Authentication Service instances
     */
    static class AuthConfig {
        /**
         * Provides static instances for the auth service
         * @return ServiceInstanceListSupplier for auth service
         */
        @Bean
        public ServiceInstanceListSupplier authServiceInstanceListSupplier() {
            return new StaticServiceInstanceListSupplier("orion-auth", Arrays.asList(
                    new DefaultServiceInstance("auth-1", "orion-auth", "orion-auth", 8091, false)
            ));
        }
    }

    /**
     * Reusable implementation for static service instance list supplier
     */
    static class StaticServiceInstanceListSupplier implements ServiceInstanceListSupplier {
        private final String serviceId;
        private final List<ServiceInstance> instances;

        /**
         * Constructor for static service instance supplier
         * @param serviceId the service identifier
         * @param instances list of available service instances
         */
        public StaticServiceInstanceListSupplier(String serviceId, List<ServiceInstance> instances) {
            this.serviceId = serviceId;
            this.instances = instances;
        }

        /**
         * Gets the service identifier
         * @return service ID
         */
        @Override
        public String getServiceId() {
            return serviceId;
        }

        /**
         * Provides the list of service instances as a reactive stream
         * @return Flux containing the list of service instances
         */
        @Override
        public Flux<List<ServiceInstance>> get() {
            return Flux.just(instances);
        }
    }
}
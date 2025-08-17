package com.unibague.gradework.oriongateway.config;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ServiceInstanceProvider {

    @Bean
    public ServiceInstanceListSupplier customServiceInstanceListSupplier() {
        return new CustomServiceInstanceListSupplier();
    }

    public static class CustomServiceInstanceListSupplier implements ServiceInstanceListSupplier {

        private final Map<String, List<ServiceInstance>> serviceInstances;

        public CustomServiceInstanceListSupplier() {
            this.serviceInstances = new HashMap<>();
            initializeServiceInstances();
        }

        private void initializeServiceInstances() {
            serviceInstances.put("orion-auth", Arrays.asList(
                    new DefaultServiceInstance("auth-1", "orion-auth", "localhost", 8091, false),
                    new DefaultServiceInstance("auth-2", "orion-auth", "localhost", 8191, false)
            ));

            serviceInstances.put("orion-user", Arrays.asList(
                    new DefaultServiceInstance("user-1", "orion-user", "localhost", 8092, false),
                    new DefaultServiceInstance("user-2", "orion-user", "localhost", 8192, false)
            ));

            serviceInstances.put("orion-program", Arrays.asList(
                    new DefaultServiceInstance("program-1", "orion-program", "localhost", 8093, false),
                    new DefaultServiceInstance("program-2", "orion-program", "localhost", 8193, false)
            ));

            serviceInstances.put("orion-document", Arrays.asList(
                    new DefaultServiceInstance("document-1", "orion-document", "localhost", 8094, false),
                    new DefaultServiceInstance("document-2", "orion-document", "localhost", 8194, false)
            ));

            serviceInstances.put("orion-drive", Arrays.asList(
                    new DefaultServiceInstance("drive-1", "orion-drive", "localhost", 8095, false),
                    new DefaultServiceInstance("drive-2", "orion-drive", "localhost", 8195, false)
            ));
        }

        @Override
        public String getServiceId() {
            return "custom-supplier";
        }

        @Override
        public Flux<List<ServiceInstance>> get() {
            return Flux.fromIterable(serviceInstances.values())
                    .flatMap(Flux::fromIterable)
                    .collectList()
                    .flux();
        }

        public Flux<List<ServiceInstance>> getByServiceId(String serviceId) {
            List<ServiceInstance> instances = serviceInstances.get(serviceId);
            return instances != null ? Flux.just(instances) : Flux.just(Arrays.asList());
        }
    }
}
package com.unibague.gradework.oriongateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Orion API Gateway
 * Entry point for the Spring Cloud Gateway microservice
 */
@SpringBootApplication
public class OrionGatewayApplication {

	/**
	 * Main method to start the application
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(OrionGatewayApplication.class, args);
	}
}

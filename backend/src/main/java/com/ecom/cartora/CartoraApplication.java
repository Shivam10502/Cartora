package com.ecom.cartora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CartoraApplication {

	public static void main(String[] args) {
		SpringApplication.run(CartoraApplication.class, args);
	}

}

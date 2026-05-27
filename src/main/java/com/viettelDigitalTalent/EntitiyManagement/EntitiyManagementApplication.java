package com.viettelDigitalTalent.EntitiyManagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class EntitiyManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(EntitiyManagementApplication.class, args);
	}

}

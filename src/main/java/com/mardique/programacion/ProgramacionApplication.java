package com.mardique.programacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProgramacionApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProgramacionApplication.class, args);
	}

}



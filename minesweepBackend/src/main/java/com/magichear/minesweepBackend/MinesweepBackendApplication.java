package com.magichear.minesweepBackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MinesweepBackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(MinesweepBackendApplication.class);
		application.setHeadless(false);
		application.run(args);
	}

}

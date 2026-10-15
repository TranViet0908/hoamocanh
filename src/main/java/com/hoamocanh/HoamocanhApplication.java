package com.hoamocanh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.hoamocanh", "vn.mocanh.studio"})
public class HoamocanhApplication {

	public static void main(String[] args) {
		SpringApplication.run(HoamocanhApplication.class, args);
	}
}
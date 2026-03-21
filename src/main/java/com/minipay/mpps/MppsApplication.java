package com.minipay.mpps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class MppsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MppsApplication.class, args);
	}

}

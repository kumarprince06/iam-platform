package com.kumarprince.iam;

import org.springframework.boot.SpringApplication;

public class TestIamPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(IamPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

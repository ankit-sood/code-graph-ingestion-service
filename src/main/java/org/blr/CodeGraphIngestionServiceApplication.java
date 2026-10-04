package org.blr;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*

*/
@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeGraphIngestionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeGraphIngestionServiceApplication.class, args);
	}

}

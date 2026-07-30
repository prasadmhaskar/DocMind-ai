package com.pnm.docmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocMindAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocMindAiApplication.class, args);
	}

}

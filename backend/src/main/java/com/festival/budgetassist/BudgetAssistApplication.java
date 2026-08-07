package com.festival.budgetassist;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BudgetAssistApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BudgetAssistApplication.class);
		if (isCliOnlyRun(args)) {
			// Import/분석 전용 실행일 때는 내장 웹서버(8080)를 띄우지 않는다 - 진짜 CLI처럼 동작하고
			// CommandLineRunner가 끝나면 프로세스도 자연히 종료된다.
			app.setWebApplicationType(WebApplicationType.NONE);
		}
		app.run(args);
	}

	private static boolean isCliOnlyRun(String[] args) {
		return Arrays.stream(args).anyMatch(arg ->
				arg.startsWith("--import.run=true") || arg.startsWith("--analysis.confidence.run=true"));
	}

}

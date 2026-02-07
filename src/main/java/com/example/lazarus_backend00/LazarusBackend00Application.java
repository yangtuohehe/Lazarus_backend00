package com.example.lazarus_backend00;
import com.example.lazarus_backend00.infrastructure.config.ModelPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;
import java.util.Map;


@SpringBootApplication()
@ComponentScan(basePackages = "com.example")
public class LazarusBackend00Application {

	public static void main(String[] args) {
		SpringApplication.run(LazarusBackend00Application.class, args);
	}

}

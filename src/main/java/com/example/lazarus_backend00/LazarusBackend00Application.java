package com.example.lazarus_backend00;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import java.util.List;
import java.util.Map;


@SpringBootApplication()
public class LazarusBackend00Application {

	public static void main(String[] args) {
		SpringApplication.run(LazarusBackend00Application.class, args);
	}

}

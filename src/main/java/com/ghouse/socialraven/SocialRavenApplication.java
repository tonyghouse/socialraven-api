package com.ghouse.socialraven;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.TimeZone;

@SpringBootApplication
@Slf4j
@EnableScheduling
public class SocialRavenApplication {

	@PostConstruct
	void started() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		log.info("Timezone set to UTC");
	}

	public static void main(String[] args) {

		SpringApplication.run(SocialRavenApplication.class, args);
	}
	
	 @Bean
	    public WebMvcConfigurer corsConfigurer()
	    {
	        return new WebMvcConfigurer() {
	            @Override
	            public void addCorsMappings(CorsRegistry registry) {
	                registry.addMapping("/**").allowedOrigins("*");
	            }
	        };
	    }
	 
	 
	 @Bean
	 public RestTemplate restTemplate() {
		 return new RestTemplate();
	 }

}

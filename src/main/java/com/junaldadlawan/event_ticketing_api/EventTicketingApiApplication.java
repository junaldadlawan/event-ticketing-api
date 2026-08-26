package com.junaldadlawan.event_ticketing_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication( exclude = {DataSourceAutoConfiguration.class} )
public class EventTicketingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventTicketingApiApplication.class, args);
	}

}

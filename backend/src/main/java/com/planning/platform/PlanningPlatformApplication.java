package com.planning.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.planning.platform.**.mapper")
@SpringBootApplication
public class PlanningPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanningPlatformApplication.class, args);
    }
}

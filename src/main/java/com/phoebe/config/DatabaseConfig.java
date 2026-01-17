package com.phoebe.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Database configuration for MyBatis.
 * MapperScan annotation enables automatic scanning of mapper interfaces.
 */
@Configuration
@MapperScan("com.phoebe.mapper")
public class DatabaseConfig {
    // MyBatis configuration is handled via application.yml
    // Schema initialization should be done manually or via Flyway/Liquibase
}

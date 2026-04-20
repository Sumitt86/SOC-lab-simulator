package com.minisoc.lab.config;

import com.minisoc.lab.model.GameState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that provides GameState as a singleton bean.
 * This ensures all services share the same GameState instance.
 */
@Configuration
public class GameStateConfig {

    @Bean
    public GameState gameState() {
        return new GameState();
    }
}

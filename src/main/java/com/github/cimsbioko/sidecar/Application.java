package com.github.cimsbioko.sidecar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.io.File;
import java.util.concurrent.Executor;

@SpringBootApplication
@EnableScheduling
public class Application {

    static final String CACHED_FILES_PATH = "/WEB-INF/cached-files";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Value("${app.data.dir}")
    File dataDir;

    @Bean
    File dataDir() {
        dataDir.mkdirs();
        return dataDir;
    }

    @Bean
    WebMvcConfigurerAdapter webMvcConfigurerAdapter() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler(CACHED_FILES_PATH + "/**")
                        .addResourceLocations(dataDir.toURI().toString());
            }
        };
    }

    @Bean
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(1);  // process events serially
        return executor;
    }

    @Bean
    public ApplicationEventMulticaster applicationEventMulticaster() {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        multicaster.setTaskExecutor(eventTaskExecutor());
        return multicaster;
    }
}

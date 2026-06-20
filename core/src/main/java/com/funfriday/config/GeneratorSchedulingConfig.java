package com.funfriday.config;

import com.funfriday.game.generator.Generator;
import com.funfriday.game.generator.GeneratorSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Configuration
@ComponentScan(basePackages = "com.funfriday")
public class GeneratorSchedulingConfig {

    @Bean
    @Primary
    public TaskScheduler generatorTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("generator-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public GeneratorScheduler generatorScheduler(ApplicationContext applicationContext, TaskScheduler taskScheduler) {
        return new GeneratorScheduler(applicationContext, taskScheduler);
    }

    // Change this to a standard Spring component, removing ApplicationRunner
    public static class GeneratorScheduler {
        private final ApplicationContext applicationContext;
        private final TaskScheduler taskScheduler;

        public GeneratorScheduler(ApplicationContext applicationContext, TaskScheduler taskScheduler) {
            this.applicationContext = applicationContext;
            this.taskScheduler = taskScheduler;
        }

        @org.springframework.context.event.EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
        public void onApplicationStart() {
            Map<String, Generator> generators = applicationContext.getBeansOfType(Generator.class);
            log.info("👉 Total Generator beans matched by TYPE: {}", generators.size());
            log.info("================================================");
            generators.forEach((beanName, generator) -> {
                GeneratorSchedule schedule = AnnotatedElementUtils.findMergedAnnotation(generator.getClass(), GeneratorSchedule.class);
                if (schedule == null || !StringUtils.hasText(schedule.interval())) {
                    return;
                }

                Duration interval = Duration.parse(schedule.interval());
                log.info("Starting generator {} with interval {}", beanName, interval);
                taskScheduler.scheduleWithFixedDelay(() -> {
                    try {
                        generator.generate();
                    } catch (Exception e) {
                        log.error("Generator failed: " + beanName, e);
                    }
                }, interval);
            });
        }
    }
}

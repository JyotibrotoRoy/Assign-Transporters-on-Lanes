package com.freightfox.transporter_assignment.config;

import com.freightfox.transporter_assignment.optimization.AssignmentOptimizer;
import com.freightfox.transporter_assignment.optimization.ExhaustiveAssignmentOptimizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OptimizerConfig {

    @Bean
    public AssignmentOptimizer assignmentOptimizer() {
        return new ExhaustiveAssignmentOptimizer();
    }
}

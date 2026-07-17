package com.bondi_mcp.mcp_stm_montevideo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class McpStmMontevideoApplication {

    static void main(String[] args) {
        SpringApplication.run(McpStmMontevideoApplication.class, args);
    }

}

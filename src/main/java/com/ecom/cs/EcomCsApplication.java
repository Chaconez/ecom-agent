package com.ecom.cs;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Hollis
 */
@SpringBootApplication
@MapperScan("com.ecom.cs.**.mapper")
public class EcomCsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomCsApplication.class, args);
    }
}

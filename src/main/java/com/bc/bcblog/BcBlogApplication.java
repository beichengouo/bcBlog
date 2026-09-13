package com.bc.bcblog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.bc.bcblog.mapper")
public class BcBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BcBlogApplication.class, args);
    }

}

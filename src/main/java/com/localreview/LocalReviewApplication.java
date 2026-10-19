package com.localreview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.localreview.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class LocalReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalReviewApplication.class, args);
    }

}

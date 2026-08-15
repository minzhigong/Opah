package com.opah.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SqliteConfig {

    @Bean
    public DataSource dataSource(@Value("${opah.data-dir}") String dataDir) throws IOException {
        Files.createDirectories(Path.of(dataDir));
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:sqlite:" + Path.of(dataDir, "opah.db"));
        ds.setDriverClassName("org.sqlite.JDBC");
        // SQLite 单写者模型，连接池收敛为 1 避免 SQLITE_BUSY
        ds.setMaximumPoolSize(1);
        ds.setPoolName("opah-sqlite");
        return ds;
    }
}

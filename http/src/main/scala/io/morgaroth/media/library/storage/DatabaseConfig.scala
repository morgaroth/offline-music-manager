package io.morgaroth.media.library.storage

import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.flywaydb.core.Flyway
import zio.*

import javax.sql.DataSource

case class DatabaseConfig(
  url: String,
  user: String,
  password: String,
)

object DatabaseConfig:
  def fromTypesafeConfig(config: Config): DatabaseConfig =
    val db = config.getConfig("music-library.postgres")
    DatabaseConfig(
      url = db.getString("url"),
      user = db.getString("user"),
      password = db.getString("password"),
    )

object DataSourceLive:
  val layer: ZLayer[DatabaseConfig, Throwable, DataSource] =
    ZLayer.scoped:
      for
        config <- ZIO.service[DatabaseConfig]
        ds <- ZIO.acquireRelease(ZIO.attempt {
          val hikariConfig = HikariConfig()
          hikariConfig.setJdbcUrl(config.url)
          hikariConfig.setUsername(config.user)
          hikariConfig.setPassword(config.password)
          hikariConfig.setMaximumPoolSize(5)
          hikariConfig.setAutoCommit(true)
          HikariDataSource(hikariConfig)
        })(ds => ZIO.succeed(ds.close()))
        _ <- ZIO.attempt {
          Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        }
      yield ds

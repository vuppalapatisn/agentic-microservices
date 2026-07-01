# Test Results

Captured: 2026-07-01 21:16:59 IST
kubectl context: `mycluster-in-che-1-cxf.4x8/d92fbhuh0h54olrrmsfg`

## 1. Unit tests (Java services)

**mvn test - product**

```
$ mvn -q test
21:17:04.457 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.amol.microservices.product.ProductApplicationTests]: ProductApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
21:17:04.495 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.amol.microservices.product.ProductApplication for test class com.amol.microservices.product.ProductApplicationTests

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

{"timestamp":"2026-07-01T15:47:04.739258Z","service":"product","level":"INFO","thread":"main","logger":"com.amol.microservices.product.ProductApplicationTests","message":"Starting ProductApplicationTests using Java 26.0.1 with PID 19346 (started by sudhakar in /Users/sudhakar/Desktop/Spring-Boot/IBM/agentic-microservices/microservices/product)"}
{"timestamp":"2026-07-01T15:47:04.743248Z","service":"product","level":"INFO","thread":"main","logger":"com.amol.microservices.product.ProductApplicationTests","message":"No active profile set, falling back to 1 default profile: \"default\""}
{"timestamp":"2026-07-01T15:47:05.004351Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.data.repository.config.RepositoryConfigurationDelegate","message":"Bootstrapping Spring Data JPA repositories in DEFAULT mode."}
{"timestamp":"2026-07-01T15:47:05.020988Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.data.repository.config.RepositoryConfigurationDelegate","message":"Finished Spring Data repository scanning in 14 ms. Found 1 JPA repository interface."}
{"timestamp":"2026-07-01T15:47:05.223776Z","service":"product","level":"INFO","thread":"main","logger":"org.hibernate.jpa.internal.util.LogHelper","message":"HHH000204: Processing PersistenceUnitInfo [name: default]"}
{"timestamp":"2026-07-01T15:47:05.249555Z","service":"product","level":"INFO","thread":"main","logger":"org.hibernate.Version","message":"HHH000412: Hibernate ORM core version 6.5.3.Final"}
{"timestamp":"2026-07-01T15:47:05.264002Z","service":"product","level":"INFO","thread":"main","logger":"org.hibernate.cache.internal.RegionFactoryInitiator","message":"HHH000026: Second-level cache disabled"}
{"timestamp":"2026-07-01T15:47:05.365117Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.orm.jpa.persistenceunit.SpringPersistenceUnitInfo","message":"No LoadTimeWeaver setup: ignoring JPA class transformer"}
{"timestamp":"2026-07-01T15:47:05.374851Z","service":"product","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.HikariDataSource","message":"HikariPool-1 - Starting..."}
{"timestamp":"2026-07-01T15:47:05.431171Z","service":"product","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.pool.HikariPool","message":"HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:productsdb user=SA"}
{"timestamp":"2026-07-01T15:47:05.431694Z","service":"product","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.HikariDataSource","message":"HikariPool-1 - Start completed."}
{"timestamp":"2026-07-01T15:47:05.442465Z","service":"product","level":"WARN","thread":"main","logger":"org.hibernate.orm.deprecation","message":"HHH90000025: H2Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)"}
{"timestamp":"2026-07-01T15:47:05.681981Z","service":"product","level":"INFO","thread":"main","logger":"org.hibernate.engine.transaction.jta.platform.internal.JtaPlatformInitiator","message":"HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)"}
{"timestamp":"2026-07-01T15:47:05.682706Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean","message":"Initialized JPA EntityManagerFactory for persistence unit 'default'"}
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by org.apache.tomcat.jni.Library in an unnamed module (file:/Users/sudhakar/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.31/tomcat-embed-core-10.1.31.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

{"timestamp":"2026-07-01T15:47:05.850633Z","service":"product","level":"WARN","thread":"main","logger":"org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration$JpaWebConfiguration","message":"spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning"}
{"timestamp":"2026-07-01T15:47:06.081372Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration","message":"H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:productsdb'"}
{"timestamp":"2026-07-01T15:47:06.108468Z","service":"product","level":"INFO","thread":"main","logger":"org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver","message":"Exposing 3 endpoints beneath base path '/actuator'"}
{"timestamp":"2026-07-01T15:47:06.140056Z","service":"product","level":"INFO","thread":"main","logger":"com.amol.microservices.product.ProductApplicationTests","message":"Started ProductApplicationTests in 1.597 seconds (process running for 1.925)"}
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/Users/sudhakar/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

**mvn test - images**

```
$ mvn -q test
21:17:08.557 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.amol.microservices.images.ImagesApplicationTests]: ImagesApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
21:17:08.591 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.amol.microservices.images.ImagesApplication for test class com.amol.microservices.images.ImagesApplicationTests

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

{"timestamp":"2026-07-01T15:47:08.786017Z","service":"images","level":"INFO","thread":"main","logger":"com.amol.microservices.images.ImagesApplicationTests","message":"Starting ImagesApplicationTests using Java 26.0.1 with PID 19377 (started by sudhakar in /Users/sudhakar/Desktop/Spring-Boot/IBM/agentic-microservices/microservices/images)"}
{"timestamp":"2026-07-01T15:47:08.790367Z","service":"images","level":"INFO","thread":"main","logger":"com.amol.microservices.images.ImagesApplicationTests","message":"No active profile set, falling back to 1 default profile: \"default\""}
{"timestamp":"2026-07-01T15:47:09.021819Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.data.repository.config.RepositoryConfigurationDelegate","message":"Bootstrapping Spring Data JPA repositories in DEFAULT mode."}
{"timestamp":"2026-07-01T15:47:09.0365Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.data.repository.config.RepositoryConfigurationDelegate","message":"Finished Spring Data repository scanning in 12 ms. Found 1 JPA repository interface."}
{"timestamp":"2026-07-01T15:47:09.204639Z","service":"images","level":"INFO","thread":"main","logger":"org.hibernate.jpa.internal.util.LogHelper","message":"HHH000204: Processing PersistenceUnitInfo [name: default]"}
{"timestamp":"2026-07-01T15:47:09.219491Z","service":"images","level":"INFO","thread":"main","logger":"org.hibernate.Version","message":"HHH000412: Hibernate ORM core version 6.5.3.Final"}
{"timestamp":"2026-07-01T15:47:09.232116Z","service":"images","level":"INFO","thread":"main","logger":"org.hibernate.cache.internal.RegionFactoryInitiator","message":"HHH000026: Second-level cache disabled"}
{"timestamp":"2026-07-01T15:47:09.31411Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.orm.jpa.persistenceunit.SpringPersistenceUnitInfo","message":"No LoadTimeWeaver setup: ignoring JPA class transformer"}
{"timestamp":"2026-07-01T15:47:09.322501Z","service":"images","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.HikariDataSource","message":"HikariPool-1 - Starting..."}
{"timestamp":"2026-07-01T15:47:09.373268Z","service":"images","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.pool.HikariPool","message":"HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:imagesdb user=SA"}
{"timestamp":"2026-07-01T15:47:09.373823Z","service":"images","level":"INFO","thread":"main","logger":"com.zaxxer.hikari.HikariDataSource","message":"HikariPool-1 - Start completed."}
{"timestamp":"2026-07-01T15:47:09.381331Z","service":"images","level":"WARN","thread":"main","logger":"org.hibernate.orm.deprecation","message":"HHH90000025: H2Dialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)"}
{"timestamp":"2026-07-01T15:47:09.612456Z","service":"images","level":"INFO","thread":"main","logger":"org.hibernate.engine.transaction.jta.platform.internal.JtaPlatformInitiator","message":"HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)"}
{"timestamp":"2026-07-01T15:47:09.613273Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean","message":"Initialized JPA EntityManagerFactory for persistence unit 'default'"}
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by org.apache.tomcat.jni.Library in an unnamed module (file:/Users/sudhakar/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.31/tomcat-embed-core-10.1.31.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

{"timestamp":"2026-07-01T15:47:09.776038Z","service":"images","level":"WARN","thread":"main","logger":"org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration$JpaWebConfiguration","message":"spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning"}
{"timestamp":"2026-07-01T15:47:09.994814Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration","message":"H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:imagesdb'"}
{"timestamp":"2026-07-01T15:47:10.020026Z","service":"images","level":"INFO","thread":"main","logger":"org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver","message":"Exposing 3 endpoints beneath base path '/actuator'"}
{"timestamp":"2026-07-01T15:47:10.049843Z","service":"images","level":"INFO","thread":"main","logger":"com.amol.microservices.images.ImagesApplicationTests","message":"Started ImagesApplicationTests in 1.425 seconds (process running for 1.705)"}
WARNING: A Java agent has been loaded dynamically (/Users/sudhakar/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
```

**mvn test - ecommerce**

```
$ mvn -q test
21:17:12.569 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.amol.microservices.ecommerce.EcommerceApplicationTests]: EcommerceApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
21:17:12.611 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.amol.microservices.ecommerce.EcommerceApplication for test class com.amol.microservices.ecommerce.EcommerceApplicationTests

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.3.5)

{"timestamp":"2026-07-01T15:47:12.826621Z","service":"ecommerce","level":"INFO","thread":"main","logger":"com.amol.microservices.ecommerce.EcommerceApplicationTests","message":"Starting EcommerceApplicationTests using Java 26.0.1 with PID 19405 (started by sudhakar in /Users/sudhakar/Desktop/Spring-Boot/IBM/agentic-microservices/microservices/ecommerce)"}
{"timestamp":"2026-07-01T15:47:12.829327Z","service":"ecommerce","level":"INFO","thread":"main","logger":"com.amol.microservices.ecommerce.EcommerceApplicationTests","message":"No active profile set, falling back to 1 default profile: \"default\""}
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by org.apache.tomcat.jni.Library in an unnamed module (file:/Users/sudhakar/.m2/repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.31/tomcat-embed-core-10.1.31.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

{"timestamp":"2026-07-01T15:47:13.336411Z","service":"ecommerce","level":"INFO","thread":"main","logger":"org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver","message":"Exposing 3 endpoints beneath base path '/actuator'"}
{"timestamp":"2026-07-01T15:47:13.358821Z","service":"ecommerce","level":"INFO","thread":"main","logger":"com.amol.microservices.ecommerce.EcommerceApplicationTests","message":"Started EcommerceApplicationTests in 0.704 seconds (process running for 1.02)"}
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/Users/sudhakar/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

**mvn test - observability-server**

```
$ mvn -q test

```


## 2. Cluster smoke checks

**kubectl get pods -n ecommerce**

```
$ kubectl get pods -n ecommerce
No resources found in ecommerce namespace.
```

**kubectl get pods -n observability**

```
$ kubectl get pods -n observability
No resources found in observability namespace.
```

**kubectl get svc -n ecommerce**

```
$ kubectl get svc -n ecommerce
No resources found in ecommerce namespace.
```

**kubectl get svc -n observability**

```
$ kubectl get svc -n observability
No resources found in observability namespace.
```


## 3. Port-forwarding

Starting port-forwards against context: mycluster-in-che-1-cxf.4x8/d92fbhuh0h54olrrmsfg

Started 7 port-forward processes (PIDs: 19453 19454 19455 19456 19457 19458 19459)

## 4. Endpoint tests

**product-service actuator health**

```
$ curl -sS http://localhost:8081/actuator/health
curl: (7) Failed to connect to localhost port 8081 after 4 ms: Couldn't connect to server
```

**image-service actuator health**

```
$ curl -sS http://localhost:8082/actuator/health
curl: (7) Failed to connect to localhost port 8082 after 1 ms: Couldn't connect to server
```

**ecommerce-service actuator health**

```
$ curl -sS http://localhost:8083/actuator/health
curl: (7) Failed to connect to localhost port 8083 after 1 ms: Couldn't connect to server
```

**GET /product-service/products**

```
$ curl -sS http://localhost:8081/product-service/products
curl: (7) Failed to connect to localhost port 8081 after 1 ms: Couldn't connect to server
```

**GET /image-service/images**

```
$ curl -sS http://localhost:8082/image-service/images
curl: (7) Failed to connect to localhost port 8082 after 1 ms: Couldn't connect to server
```

**GET /ecommerce-service/ecommerceProducts**

```
$ curl -sS http://localhost:8083/ecommerce-service/ecommerceProducts
curl: (7) Failed to connect to localhost port 8083 after 1 ms: Couldn't connect to server
```

**POST /ecommerce-service/apply-coupon**

```
$ curl -sS -X POST http://localhost:8083/ecommerce-service/apply-coupon -H 'Content-Type: text/plain' -d 'ABC123'
curl: (7) Failed to connect to localhost port 8083 after 1 ms: Couldn't connect to server
```


## 5. Observability stack

**observability-server services**

```
$ curl -sS http://localhost:8091/api/observability/services
curl: (7) Failed to connect to localhost port 8091 after 1 ms: Couldn't connect to server
```

**observability-debug-agent health**

```
$ curl -sS http://localhost:8092/health
{"status":"UP"}
```

**Prometheus readiness**

```
$ curl -sS http://localhost:9090/-/ready
Prometheus Server is Ready.
```

**Grafana health**

```
$ curl -sS http://localhost:3000/api/health
{
  "commit": "13173c9874af312fe75545f52aa6539af02076ac",
  "database": "ok",
  "version": "11.1.4"
}
```



Whale Framework 预编译 Maven 本地仓库（已随本 Git 仓库携带，plain JAR，未混淆）。



- 构建时启用 Maven Profile：binary-framework

- 勿删除本目录；Framework 源码不随本仓库公开



仓库根目录：

  mvn clean package -Pbinary-framework -pl whale-health/health-app -am -DskipTests

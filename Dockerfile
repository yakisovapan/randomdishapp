# ベースイメージとして OpenJDK 17 を使用
FROM openjdk:17-jdk-slim

# Mavenをインストール
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# 作業ディレクトリを設定
WORKDIR /app

# Mavenプロジェクトファイルをコピーして依存関係をダウンロード（キャッシュを有効活用するため）
COPY pom.xml .
COPY src ./src
# アプリケーションの依存関係をビルド
RUN mvn dependency:go-offline -B

# アプリケーションをビルド
COPY . .
RUN mvn package -DskipTests

# 実行可能なJARファイルの名前を特定 (通常はartifactid-version.jarの形式)
ARG JAR_FILE=target/*.jar

# ポート 8080 を公開
EXPOSE 8080

# アプリケーションを実行
ENTRYPOINT ["java", "-jar", "target/myrandomdishapp-0.0.1-SNAPSHOT.jar"]
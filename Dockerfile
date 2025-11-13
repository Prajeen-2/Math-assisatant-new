# STEP 1: Use official Java 17 JDK
FROM eclipse-temurin:17-jdk

# STEP 2: Create working directory
WORKDIR /app

# STEP 3: Copy everything
COPY . .

# STEP 4: Compile Java
RUN javac FibonacciServer.java

# STEP 5: Render will inject PORT env var
env PORT=8080

# STEP 6: Start server
CMD ["java", "FibonacciServer"]

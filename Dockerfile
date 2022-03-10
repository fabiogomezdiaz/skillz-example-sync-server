################
# STAGE: Build #
################
FROM gradle:6.9.2-jdk8 as builder

# Create Working Directory
ENV BUILD_DIR=/home/gradle/app/
RUN mkdir $BUILD_DIR
WORKDIR $BUILD_DIR
COPY . $BUILD_DIR

# Create Build
RUN gradle jar

#################
# STAGE: Deploy #
#################
FROM amazoncorretto:11

# Install dependencies
RUN yum install -y shadow-utils

# Create app directory
ENV APP_HOME=/realtime-server
RUN mkdir -p ${APP_HOME}/scripts
WORKDIR ${APP_HOME}

# Copy files from the builder stage
COPY --from=builder /home/gradle/app/example_sync_server/build/libs/example_sync_server.jar $APP_HOME/server.jar

# Copy files
COPY example_sync_server/resources ${APP_HOME}/resources
COPY healthcheck.sh ${APP_HOME}

# Making JAR and Healthcheck files executable
RUN chmod +x ${APP_HOME}/*.jar
RUN chmod +x ${APP_HOME}/*.sh

# Adding non-root user
RUN useradd --uid 1000 appl

# Allow non-root user into the app directory
RUN chown -R appl ${APP_HOME}

# Switch to the non-root user
USER appl

# Set configuration enviroment variables
ENV scriptsPath=${APP_HOME}/scripts/
ENV SYNC_RELEASE_CONFIGURATION=release

# Start the server
CMD ["java", "-jar", "server.jar"]

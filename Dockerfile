FROM ubuntu:22.04
WORKDIR /SimRunner
RUN apt-get update && apt-get -y install\
	default-jre
ADD ./bin/SimRunner.jar .
ADD ./bin/config.json .
CMD ["java", "-jar", "SimRunner.jar", "config.json"]
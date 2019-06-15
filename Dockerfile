FROM ubuntu:18.04
LABEL author.name="Wonkyo Choe"
LABEL author.email="heysid@ajou.ac.kr"
LABEL description="Ajou Univ > 2019 Spring > Distributed and Parallel Programming > Group 3"


COPY src /src
WORKDIR /src
VOLUME /src
EXPOSE 8000 8001 8002 8003 8004
EXPOSE 7000 7001 7002 7003 7004 

RUN bash /src/init_java.sh \
        sudo

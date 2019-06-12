#!/bin/bash

docker build -t dapp:server -f Dockerfile.server .

docker run -d --name dapp_master \
    dapp:server \
    bash -c "javac /src/dapp/Master.java && java dapp.Master"

docker run -d --name dapp_slave1 --net container:dapp_master dapp:server bash -c "javac /src/dapp/Slave.java && java dapp.Slave 7001"
docker run -d --name dapp_slave2 --net container:dapp_master dapp:server bash -c "javac /src/dapp/Slave.java && java dapp.Slave 7002"
docker run -d --name dapp_slave3 --net container:dapp_master dapp:server bash -c "javac /src/dapp/Slave.java && java dapp.Slave 7003"
docker run -d --name dapp_slave4 --net container:dapp_master dapp:server bash -c "javac /src/dapp/Slave.java && java dapp.Slave 7004"

docker attach dapp_master

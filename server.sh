#!/bin/bash

docker build -t dapp:server -f Dockerfile .

docker run -d -p 7000:7000 --name dapp_master \
    dapp:server \
    bash -c "java dapp.Master"

# Not working yet..
docker run -d --name dapp_slave1 --net container:dapp_master dapp:server bash -c  "java dapp.Slave 7001"
# docker run -d --name dapp_slave2 --net container:dapp_master dapp:server bash -c "java dapp.Slave 7002"
# docker run -d --name dapp_slave3 --net container:dapp_master dapp:server bash -c "java dapp.Slave 7003"
# docker run -d --name dapp_slave4 --net container:dapp_master dapp:server bash -c "java dapp.Slave 7004"

# docker attach dapp_master

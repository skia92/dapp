#!/bin/bash

#docker build -t dapp:server -f Dockerfile .

docker run -it --name dapp_client --net container:dapp_master dapp:server bash 

## Monitor client
# docker attach dapp_client
#
## Start client
#docker start -i dapp_client
#
## Stop client
#docker stop dapp_client
#
## Remove client docker container
#docker rm dapp_client

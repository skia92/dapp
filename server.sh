#!/bin/bash

docker build -t dapp:server -f Dockerfile.server .
docker run -it --name dapp_node1 --net dapp_nw -d dapp:server bash
# docker run --name dapp_node2 --net dapp_nw -d dapp:server

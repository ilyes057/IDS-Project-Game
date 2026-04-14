#!/bin/bash

for q in queue.A queue.B queue.C queue.D
do
  sudo rabbitmqctl purge_queue "$q"
done
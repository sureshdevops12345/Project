#!/bin/bash
cd /home/rarora/careoregon
source ./careoregon/bin/activate
/home/rarora/careoregon/careoregon/bin/python /home/rarora/careoregon/co-daily-process.py
deactivate

#!/bin/bash
dataDiffRoot=`pwd`
mkdir -p data logs out arch
virtualenv careoregon
source careoregon/bin/activate
pip install csvdiff logging
deactivate

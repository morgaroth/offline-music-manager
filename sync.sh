#!/usr/bin/env bash

rsync -avvh --inplace --delete --exclude cache $HOME/music-library/ $1
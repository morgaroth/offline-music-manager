#!/usr/bin/env bash

rsync -avvh --inplace --delete --exclude cache /home/morgaroth/projects/MusicLibrary/music/ $1
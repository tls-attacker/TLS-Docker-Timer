docker run -v cert-data:/data --name helper busybox true
docker cp specificCerts/_data/. helper:/data
docker rm helper

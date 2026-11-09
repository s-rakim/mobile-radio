#!/bin/sh

# Start Tailscale in the background using userspace networking
echo "Starting Tailscale..."
tailscaled --tun=userspace-networking --socks5-server=localhost:1055 &

# Wait for the daemon to start
sleep 3

# Authenticate Tailscale
echo "Connecting to Tailnet..."
tailscale up --authkey=${TS_AUTHKEY} --hostname=spitify-render --accept-routes

# Wait until Tailscale is fully connected to the network
echo "Waiting for Tailscale connection..."
tailscale status > /dev/null 2>&1
while [ $? -ne 0 ]; do
  sleep 2
  tailscale status > /dev/null 2>&1
done
echo "Tailscale connected!"

# Download gost for proxy chaining
echo "Installing gost..."
wget -q https://github.com/ginuerzh/gost/releases/download/v2.11.5/gost-linux-amd64-2.11.5.gz
gzip -d gost-linux-amd64-2.11.5.gz
mv gost-linux-amd64-2.11.5 /usr/local/bin/gost
chmod +x /usr/local/bin/gost

# Start gost proxy chain: local 1080 -> Tailscale 1055 -> Phone 100.108.104.24:1080
echo "Starting gost proxy chain..."
gost -L=socks5://127.0.0.1:1080 -F=socks5://127.0.0.1:1055 -F=socks5://100.108.104.24:1080 &

# Start the Node.js backend
echo "Starting Node..."
node index.js
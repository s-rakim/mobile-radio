#!/bin/sh

# Start Tailscale in the background using userspace networking
echo "Starting Tailscale..."
tailscaled --tun=userspace-networking --socks5-server=localhost:1055 &

# Wait for the daemon to start
sleep 3

# Authenticate Tailscale
echo "Connecting to Tailnet..."
tailscale up --authkey=${TS_AUTHKEY} --hostname=spitify-render --accept-routes

# Wait until Tailscale is fully connected
echo "Waiting for Tailscale connection..."
while ! tailscale status > /dev/null 2>&1; do
  sleep 2
done
echo "Tailscale connected!"

# --- DIAGNOSTIC TEST ---
echo "Testing proxy connection via curl..."
if curl -x socks5h://100.108.104.24:8080 -s -m 10 https://ifconfig.me; then
    echo ""
    echo "✅ SUCCESS: Phone proxy is working!"
else
    echo "❌ ERROR: Phone proxy is blocking connections."
fi
# -----------------------

# Download gost v2
echo "Installing gost v2..."
wget -q https://github.com/ginuerzh/gost/releases/download/v2.11.5/gost-linux-amd64-2.11.5.gz
gzip -d gost-linux-amd64-2.11.5.gz
mv gost-linux-amd64-2.11.5 /usr/local/bin/gost
chmod +x /usr/local/bin/gost

# Start gost proxy chain (Uses port 8080 for the phone)
echo "Starting gost proxy chain..."
gost -L=socks5://127.0.0.1:1080 -L=http://127.0.0.1:8080 -F=socks5h://127.0.0.1:1055 -F=socks5h://100.108.104.24:8080 > /dev/null 2>&1 &

# Start the Node.js backend
echo "Starting Node..."
node index.js
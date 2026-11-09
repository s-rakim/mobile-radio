#!/bin/sh
set -e

PHONE="${PHONE_PROXY:-100.108.104.24:8080}"
TS="tailscale --socket=/tmp/tailscaled.sock"

if [ -z "$TS_AUTHKEY" ]; then
  echo "TS_AUTHKEY not set - add it in Render's environment settings"
  exit 1
fi

echo "Starting Tailscale..."
tailscaled --tun=userspace-networking \
  --socks5-server=localhost:1055 \
  --state=mem: \
  --socket=/tmp/tailscaled.sock &
sleep 3

$TS up --authkey="${TS_AUTHKEY}" --hostname="${TS_HOSTNAME:-spitify-render}" --accept-routes

until $TS status >/dev/null 2>&1; do sleep 2; done
echo "Tailscale connected"

echo "Starting proxy chain -> phone ($PHONE)..."
gost -L=socks5://127.0.0.1:1080 -L=http://127.0.0.1:8118 \
     -F=socks5://127.0.0.1:1055 \
     -F=socks5://$PHONE &
sleep 2

echo "Testing chain..."
curl -sS -m 20 -x socks5h://127.0.0.1:1080 https://ifconfig.me \
  && echo " <- exit IP (should be the phone's)" \
  || echo "Chain test FAILED - check phone, pproxy and Tailscale"

exec node index.js
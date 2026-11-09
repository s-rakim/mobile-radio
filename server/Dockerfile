FROM node:20-slim

# Install system dependencies and Python
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    curl \
    wget \
    gzip \
    && rm -rf /var/lib/apt/lists/*

# Copy Tailscale binaries directly from the official image (fast & lean)
COPY --from=docker.io/tailscale/tailscale:stable \
     /usr/local/bin/tailscaled /usr/local/bin/tailscale /usr/local/bin/

# Install Deno via npm (required for yt-dlp-ejs)
RUN npm install -g deno

# Install gost v2 for the proxy chain
RUN wget -q https://github.com/ginuerzh/gost/releases/download/v2.11.5/gost-linux-amd64-2.11.5.gz \
    && gzip -d gost-linux-amd64-2.11.5.gz \
    && mv gost-linux-amd64-2.11.5 /usr/local/bin/gost \
    && chmod +x /usr/local/bin/gost

# Install yt-dlp, EJS plugin, POT provider, AND PySocks
RUN pip3 install --break-system-packages -U yt-dlp yt-dlp-ejs bgutil-ytdlp-pot-provider PySocks

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY . .

# Fix Windows CRLF line endings and make the script executable
RUN sed -i 's/\r$//' start.sh && chmod +x start.sh

EXPOSE 3000
CMD ["./start.sh"]